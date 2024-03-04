/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
use core::cmp::Ordering;
use std::cmp::{max, min};
use std::collections::{HashMap, HashSet};
use std::fmt::{Debug, Display, Formatter};
use std::fs;
use std::fs::File;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::time::Instant;

use csv::{Reader, StringRecord, Trim};
use error_chain::{bail, error_chain};
use icu_collator::{CaseLevel, Collator, CollatorOptions, Strength};
use icu_locid::Locale;
use indicatif::ProgressIterator;
use oracle::{Connection, Connector, Privilege, Statement};
use oracle::sql_type::OracleType;
use reqwest::Url;

use crate::OrderMapping::{IntegerOffset, LUT};
use crate::UnicodeCaseFoldingVariant::{Full, Simple};

error_chain! {
    foreign_links {
        Io(std::io::Error);
        HttpRequest(reqwest::Error);
        CSV(csv::Error);
        OracleDB(oracle::Error);
    }
}

/// refers to the index of a codepoint or string in a global index
type IElement = usize;

const FILE_FORMAT_VERSION: u16 = 0;
const OUTPUT_FOLDER: &str = "./out";
const PATH_GRAAL_REPO: &str = "../../../../../";
const PATH_CASE_FOLD_DATA: &str = "regex/src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/tregex/parser/CaseFoldData.java";
const PATH_ORACLE_DB_CONSTANTS: &str = "regex/src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/tregex/parser/flavors/OracleDBConstants.java";
const PATH_ORACLE_DB_TESTS: &str = "regex/src/com.oracle.truffle.regex.test/src/com/oracle/truffle/regex/tregex/test/OracleDBTests.java";
const GENERATED_CODE_MARKER_BEGIN: &str = "    /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */";
const GENERATED_CODE_MARKER_END: &str = "    /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */";

#[derive(Debug, Clone)]
struct CollationElement {
    string: String,
}

#[derive(Debug, Clone)]
struct CollationElementIndex {
    index_base: usize,
    index_src: usize,
    index_dst: usize,
    element: CollationElement,
}

#[derive(Debug, Clone, Eq, PartialEq)]
enum EqMapping {
    IntegerOffset(i32),
    Set(usize),
    AlternatingAL,
    AlternatingUL,
    Single(IElement),
}

impl EqMapping {
    fn from_single_mapping(src: IElement, dst: IElement) -> EqMapping {
        let offset = (dst as i32) - (src as i32);
        if offset == 1 {
            if src & 1 == 0 { EqMapping::AlternatingAL } else { EqMapping::AlternatingUL }
        } else {
            EqMapping::IntegerOffset(offset)
        }
    }
}

#[derive(Debug)]
enum OrderMapping {
    IntegerOffset(i32),
    LUT(Vec<usize>),
}

trait RangeMapping<T> {
    fn lo(&self) -> IElement;
    fn hi(&self) -> IElement;
    fn mapping(&self) -> &T;
}

#[derive(Debug)]
struct OrderTableEntry {
    lo: usize,
    hi: usize,
    mapping: OrderMapping,
}

impl RangeMapping<OrderMapping> for OrderTableEntry {
    fn lo(&self) -> IElement {
        self.lo
    }

    fn hi(&self) -> IElement {
        self.hi
    }

    fn mapping(&self) -> &OrderMapping {
        &self.mapping
    }
}

#[derive(Debug, Clone, Eq, PartialEq)]
struct EqTableEntry {
    lo: IElement,
    hi: IElement,
    mapping: EqMapping,
}

impl EqTableEntry {
    fn as_dummy(&self) -> EqTableEntry {
        EqTableEntry {
            lo: self.lo,
            hi: self.hi,
            mapping: EqMapping::IntegerOffset(0),
        }
    }

    fn with_hi(&self, hi: IElement) -> EqTableEntry {
        EqTableEntry {
            lo: self.lo,
            hi,
            mapping: self.mapping.clone(),
        }
    }

    fn with_lo(&self, lo: IElement) -> EqTableEntry {
        EqTableEntry {
            lo,
            hi: self.hi,
            mapping: self.mapping.clone(),
        }
    }

    fn with_mapping(&self, mapping: EqMapping) -> EqTableEntry {
        EqTableEntry {
            lo: self.lo,
            hi: self.hi,
            mapping,
        }
    }
}

impl RangeMapping<EqMapping> for EqTableEntry {
    fn lo(&self) -> IElement {
        self.lo
    }

    fn hi(&self) -> IElement {
        self.hi
    }

    fn mapping(&self) -> &EqMapping {
        &self.mapping
    }
}

trait RangeMappingTable<T, M: Debug + RangeMapping<T>> {
    fn table(&self) -> &Vec<M>;

    fn binary_search(&self, key: IElement) -> Option<&M> {
        let table = self.table();
        let mut lo: i32 = 0;
        let mut hi: i32 = (table.len() as i32) - 1;
        while lo <= hi {
            let mid = (lo + hi) >> 1;
            let mid_val = table[mid as usize].lo();
            if mid_val < key {
                lo = mid + 1;
            } else if mid_val > key {
                hi = mid - 1;
            } else {
                assert!(table[mid as usize].lo() <= key && key <= table[mid as usize].hi(), "{:?}, key: {}", table[mid as usize], key);
                return Some(&table[mid as usize]);
            }
        }
        if lo > 0 && table[(lo - 1) as usize].lo() <= key && key <= table[(lo - 1) as usize].hi() {
            return Some(&table[(lo - 1) as usize]);
        }
        return None;
    }
}

struct OrderTable {
    table: Vec<OrderTableEntry>,
}

impl RangeMappingTable<OrderMapping, OrderTableEntry> for OrderTable {
    fn table(&self) -> &Vec<OrderTableEntry> {
        &self.table
    }
}

struct EqTable {
    table: Vec<EqTableEntry>,
    sets: Vec<Vec<IElement>>,
}

impl RangeMappingTable<EqMapping, EqTableEntry> for EqTable {
    fn table(&self) -> &Vec<EqTableEntry> {
        &self.table
    }
}

impl OrderTable {
    /// Creates a new compressed table from an exhaustive list of collation elements `full_map` mapping `index_src` to `index_dst`.
    /// `full_map` must be sorted by `index_src`.
    ///
    fn create(full_map: &Vec<CollationElementIndex>, index_src: fn(&CollationElementIndex) -> usize, index_dst: fn(&CollationElementIndex) -> usize) -> OrderTable {
        fn push_entry(full_map: &Vec<CollationElementIndex>,
                      index_src: fn(&CollationElementIndex) -> usize,
                      index_dst: fn(&CollationElementIndex) -> usize,
                      table: &mut Vec<OrderTableEntry>, last_range_end: usize, prev: usize, cur_index_src: usize) {
            let last_index_src = index_src(&full_map[last_range_end]);
            let prev_index_src = index_src(&full_map[prev]);
            // if range size is 1, use a lookup table
            if (cur_index_src - last_index_src) == 1 {
                // if the last entry in the mapping is already a lookup table, append to it
                if table.last_mut().map(|e| {
                    match &mut e.mapping {
                        LUT(lut) => {
                            assert_eq!(e.hi, prev_index_src - 1, "lookup table must be adjacent to current element");
                            e.hi = prev_index_src;
                            lut.push(index_dst(&full_map[prev]));
                            false
                        }
                        _ => true
                    }
                }).unwrap_or(true) {
                    // otherwise, create a new lookup table
                    table.push(OrderTableEntry {
                        lo: last_index_src,
                        hi: last_index_src,
                        mapping: LUT(vec![index_dst(&full_map[prev])]),
                    });
                }
            } else {
                // range size is greater than one, create an integer offset mapping
                table.push(OrderTableEntry {
                    lo: last_index_src,
                    hi: prev_index_src,
                    mapping: IntegerOffset((index_dst(&full_map[last_range_end]) as i32) - (last_index_src as i32)),
                });
            }
        }

        let mut table: Vec<OrderTableEntry> = Vec::new();
        let mut last_range_end = 0;
        // try to find consecutive ranges in the mapping that can be expressed with integer offsets, e.g. [1..4] -> [3..6]
        for i in 1..full_map.len() {
            let prev = i - 1;
            if index_src(&full_map[prev]) != index_src(&full_map[i]) - 1 || index_dst(&full_map[prev]) != index_dst(&full_map[i]) - 1 {
                push_entry(&full_map, index_src, index_dst, &mut table, last_range_end, prev, index_src(&full_map[i]));
                last_range_end = i;
            }
        }
        push_entry(&full_map, index_src, index_dst, &mut table, last_range_end, full_map.len() - 1, index_src(&full_map[full_map.len() - 1]) + 1);
        OrderTable { table }
    }

    /// Returns the `dst_index` for a given `src_index`
    fn lookup(&self, key: usize) -> usize {
        self.binary_search(key).map(|e| {
            return match &e.mapping {
                IntegerOffset(offset) => {
                    ((key as i32) + offset) as usize
                }
                LUT(lut) => {
                    lut[key - e.lo]
                }
            };
        }).unwrap_or(key)
    }

    #[allow(dead_code)]
    fn print_size(&self, name: &str) {
        let size = self.table.iter().map(|e| {
            match &e.mapping {
                IntegerOffset(_) => { 12 }
                LUT(lut) => { 8 + (lut.len() * 4) }
            }
        }).reduce(|a, b| a + b).unwrap_or(0);
        println!("{:>25} size: {:>6} bytes", name, size);
    }
}

impl EqTable {
    /// Creates a table mapping all equivalent collation elements in the given exhaustive list `full_map` to each other.
    /// `full_map` must be sorted by the collator, so that equivalent elements are next to each other.
    ///
    fn create<F: Fn(&str, &str) -> Ordering>(collator: F, full_map: &Vec<CollationElementIndex>) -> EqTable {
        let mut eq_map_0: Vec<EqTableEntry> = Vec::with_capacity(full_map.len());
        let mut eq_sets: Vec<Vec<IElement>> = Vec::new();
        let mut buf: Vec<IElement> = Vec::new();
        // first pass: find equivalent elements and create mappings
        for i in 1..full_map.len() {
            if collator(&full_map[i - 1].element.string, &full_map[i].element.string) == Ordering::Equal {
                if buf.is_empty() {
                    buf.push(full_map[i - 1].index_base);
                }
                buf.push(full_map[i].index_base);
            } else {
                if !buf.is_empty() {
                    EqTable::eq_map_push_first_pass(&mut eq_map_0, &mut eq_sets, &buf);
                    buf.clear();
                }
            }
        }
        if !buf.is_empty() {
            EqTable::eq_map_push_first_pass(&mut eq_map_0, &mut eq_sets, &buf);
        }
        for eq_table in &mut eq_sets {
            eq_table.sort();
        }
        EqTable { table: EqTable::eq_map_merge_adjacent(&mut eq_map_0), sets: eq_sets }
    }

    fn from_vec<'a>(mut equivalences: Vec<Vec<IElement>>) -> EqTable {
        for vec in equivalences.iter_mut() {
            vec.sort();
        }
        equivalences.sort();
        let mut eq_map_0: Vec<EqTableEntry> = Vec::with_capacity(equivalences.len());
        let mut eq_sets: Vec<Vec<IElement>> = Vec::new();
        // first pass: find equivalent elements and create mappings
        for buf in equivalences {
            if buf.len() > 1 {
                EqTable::eq_map_push_first_pass(&mut eq_map_0, &mut eq_sets, &buf);
            }
        }
        for eq_table in &mut eq_sets {
            eq_table.sort();
        }
        EqTable { table: EqTable::eq_map_merge_adjacent(&mut eq_map_0), sets: eq_sets }
    }

    fn eq_map_push_first_pass(eq_map_0: &mut Vec<EqTableEntry>, eq_tables: &mut Vec<Vec<IElement>>, buf: &Vec<IElement>) {
        if buf.len() == 2 {
            let offset = (buf[0] as i32) - (buf[1] as i32);
            if offset.abs() == 1 {
                // elements indices are adjacent, we can map them with AlternatingAL/UL
                let min = min(buf[0], buf[1]);
                let max = max(buf[0], buf[1]);
                eq_map_0.push(EqTableEntry {
                    lo: min,
                    hi: max,
                    mapping: if min & 1 == 0 { EqMapping::AlternatingAL } else { EqMapping::AlternatingUL },
                });
            } else {
                // indices are not adjacent, map both with integer offset
                eq_map_0.push(EqTableEntry {
                    lo: buf[0],
                    hi: buf[0],
                    mapping: EqMapping::IntegerOffset((buf[1] as i32) - (buf[0] as i32)),
                });
                eq_map_0.push(EqTableEntry {
                    lo: buf[1],
                    hi: buf[1],
                    mapping: EqMapping::IntegerOffset(offset),
                });
            }
        } else {
            // more than two equivalent elements, we need a set
            for i in buf {
                eq_map_0.push(EqTableEntry {
                    lo: *i,
                    hi: *i,
                    mapping: EqMapping::Set(eq_tables.len()),
                });
            }
            eq_tables.push(buf.to_vec());
        }
    }

    fn eq_map_merge_adjacent(eq_map_0: &mut Vec<EqTableEntry>) -> Vec<EqTableEntry> {
        // merge adjacent mappings into range-based entries, e.g. `1 -> offset(10), 2 -> offset(10) becomes [1-2] -> offset(10)
        eq_map_0.sort_by_key(|x| x.lo);
        let mut eq_map: Vec<EqTableEntry> = Vec::new();
        eq_map.push(eq_map_0[0].clone());
        for e in &eq_map_0[1..] {
            let last = eq_map.last_mut().unwrap();
            if last.hi == e.lo - 1 && last.mapping == e.mapping {
                last.hi = e.hi;
            } else {
                eq_map.push(e.clone());
            }
        }
        eq_map
    }

    fn create_one_way_mapping(mappings: Vec<(IElement, IElement)>) -> EqTable {
        fn can_use_single_mapping(last: &EqTableEntry, dst: IElement) -> bool {
            match last.mapping {
                EqMapping::IntegerOffset(offset) => {
                    last.lo == last.hi && last.lo as i32 + offset == dst as i32
                }
                EqMapping::Single(last_dst) => {
                    last_dst == dst
                }
                _ => false
            }
        }

        assert!(mappings.len() > 0);
        let mut table: Vec<EqTableEntry> = vec![];
        let (src_0, dst_0) = mappings[0];
        table.push(EqTableEntry {
            lo: src_0,
            hi: src_0,
            mapping: EqMapping::from_single_mapping(src_0, dst_0),
        });
        for (src, dst) in mappings[1..].iter().cloned() {
            let last = table.last().unwrap();
            assert!(src > last.hi);
            let mapping = EqMapping::from_single_mapping(src, dst);
            if can_use_single_mapping(last, dst) {
                table.last_mut().unwrap().mapping = EqMapping::Single(dst);
                table.last_mut().unwrap().hi = src;
            } else if mapping == last.mapping && src == last.hi + (match mapping {
                EqMapping::IntegerOffset(_) => { 1 }
                EqMapping::Set(_) => { 1 }
                EqMapping::AlternatingAL => { 2 }
                EqMapping::AlternatingUL => { 2 }
                EqMapping::Single(_) => { 1 }
            }) {
                table.last_mut().unwrap().hi = src;
            } else {
                table.push(EqTableEntry {
                    lo: src,
                    hi: src,
                    mapping,
                });
            }
        }
        return EqTable { table, sets: vec![] };
    }

    /// Creates a diff-based equivalence table from a given full table `child` and parent mapping `parent`,
    /// such that mappings that are equal in both `parent` and `child` are removed from the new table.
    ///
    fn create_diff(parent: &EqTable, child: &EqTable) -> EqTable {
        fn mapping_eq(parent: &EqTable, child: &EqTable, cur_parent: &EqTableEntry, cur_child: &EqTableEntry) -> bool {
            match (&cur_parent.mapping, &cur_child.mapping) {
                (EqMapping::Set(lut_parent), EqMapping::Set(lut_child)) => {
                    parent.sets[*lut_parent].eq(&child.sets[*lut_child])
                }
                (a, b) => { a.eq(b) }
            }
        }
        fn mapping_clone(child: &EqTable, cur_child: &EqTableEntry, eq_table_diff: &mut Vec<EqTableEntry>, sets_diff: &mut Vec<Vec<IElement>>, sets_map: &mut Vec<Option<usize>>) {
            if eq_table_diff.last().map(|last| last.hi == cur_child.hi).unwrap_or(false) {
                return;
            }
            match cur_child.mapping {
                EqMapping::Set(set_index) => {
                    match &sets_map[set_index] {
                        Some(mapped_index) => {
                            eq_table_diff.push(cur_child.with_mapping(EqMapping::Set(*mapped_index)));
                        }
                        None => {
                            eq_table_diff.push(cur_child.with_mapping(EqMapping::Set(sets_diff.len())));
                            sets_map[set_index] = Some(sets_diff.len());
                            sets_diff.push(child.sets[set_index].clone());
                        }
                    }
                }
                _ => {
                    eq_table_diff.push(cur_child.clone());
                }
            }
        }

        let mut eq_table_diff: Vec<EqTableEntry> = Vec::with_capacity(child.table.len());
        let mut lut_diff: Vec<Vec<IElement>> = Vec::with_capacity(child.sets.len());
        let mut lut_map: Vec<Option<usize>> = vec![None; child.sets.len()];
        let mut i_parent = parent.table.iter();
        let mut i_child = child.table.iter();
        let mut next_parent = i_parent.next();
        let mut next_child = i_child.next();
        let mut tmp;
        loop {
            match (next_parent, next_child) {
                (Some(cur_parent), Some(cur_child)) => {
                    if cur_parent.hi < cur_child.lo {
                        // parent mapping not present in child - overwrite with dummy
                        eq_table_diff.push(cur_parent.as_dummy());
                        next_parent = i_parent.next();
                    } else if cur_child.hi < cur_parent.lo {
                        // child mapping not present in parent - keep
                        mapping_clone(child, cur_child, &mut eq_table_diff, &mut lut_diff, &mut lut_map);
                        next_child = i_child.next();
                    } else {
                        // ranges intersect
                        if cur_parent.lo < cur_child.lo {
                            // parent mapping partially not present in child, overwrite non-intersecting lower range with dummy
                            assert!(cur_parent.hi >= cur_child.lo, "{:?}, {:?}", cur_parent, cur_child);
                            eq_table_diff.push(cur_parent.with_hi(cur_child.lo - 1).as_dummy());
                        }
                        if cur_child.lo < cur_parent.lo || !mapping_eq(&parent, &child, &cur_parent, &cur_child) {
                            // child mapping partially not present in parent, or not equal, keep
                            mapping_clone(child, cur_child, &mut eq_table_diff, &mut lut_diff, &mut lut_map);
                        }
                        if cur_parent.hi > cur_child.hi {
                            // remove intersecting part of parent range
                            tmp = cur_parent.with_lo(cur_child.hi + 1);
                            next_parent = Some(&tmp);
                            next_child = i_child.next();
                        } else if cur_child.hi > cur_parent.hi {
                            // remove intersecting part of child range
                            tmp = cur_child.with_lo(cur_parent.hi + 1);
                            next_child = Some(&tmp);
                            next_parent = i_parent.next();
                        } else {
                            next_child = i_child.next();
                            next_parent = i_parent.next();
                        }
                    }
                }
                (Some(cur_parent), None) => {
                    // parent mapping not present in child - overwrite with dummy
                    eq_table_diff.push(cur_parent.as_dummy());
                    next_parent = i_parent.next();
                }
                (None, Some(cur_child)) => {
                    // child mapping not present in parent - keep
                    mapping_clone(child, cur_child, &mut eq_table_diff, &mut lut_diff, &mut lut_map);
                    next_child = i_child.next();
                }
                (None, None) => {
                    break;
                }
            }
        }
        let diff = EqTable { table: eq_table_diff, sets: lut_diff };
        for e in &child.table {
            for i in e.lo..e.hi {
                let vec1: Vec<IElement> = child.lookup(i).unwrap();
                let vec2: Vec<IElement> = diff.lookup(i).unwrap_or_else(|| { parent.lookup(i).unwrap() });
                assert_eq!(HashSet::<IElement>::from_iter(vec1), HashSet::<IElement>::from_iter(vec2), "");
            }
        }
        diff
    }

    fn lookup(&self, key: IElement) -> Option<Vec<IElement>> {
        self.binary_search(key).map(|e| {
            return match &e.mapping {
                EqMapping::IntegerOffset(o) => {
                    vec![key, (o + (key as i32)) as IElement]
                }
                EqMapping::Set(i) => {
                    self.sets[*i].clone()
                }
                EqMapping::AlternatingAL => {
                    vec![key, key ^ 1]
                }
                EqMapping::AlternatingUL => {
                    vec![key, ((key - 1) ^ 1) + 1]
                }
                EqMapping::Single(value) => {
                    vec![key, *value]
                }
            };
        })
    }

    #[allow(dead_code)]
    fn print_size(&self) {
        println!("{:>25} size: {:>6} bytes", "equivalence table", (self.table.len() * 3 +
            self.sets.len() +
            self.sets.iter().map(|x| x.len()).reduce(|a, b| a + b).unwrap_or(0)) * 4);
    }

    fn dump_java(&self, out: &mut Vec<u8>, name: &str, parent: Option<&str>) -> Result<()> {
        writeln!(out, "private static final CaseFoldEquivalenceTable {} = new CaseFoldEquivalenceTable({}, new CodePointSet[] {{", name, parent.unwrap_or("null"))?;
        for set in &self.sets {
            writeln!(out, "rangeSet({}),", list_to_ranges_str(set))?;
        }
        write!(out, "}},")?;
        self.dump_java_table(out)?;
        Ok(())
    }

    fn dump_java_one_way(&self, out: &mut Vec<u8>, name: &str, parent: Option<&str>) -> Result<()> {
        write!(out, "private static final CaseFoldTable {} = new CaseFoldTable({}, ", name, parent.unwrap_or("null"))?;
        assert!(self.sets.is_empty());
        self.dump_java_table(out)?;
        Ok(())
    }

    fn dump_java_table(&self, out: &mut Vec<u8>) -> Result<()> {
        writeln!(out, "new int[] {{")?;
        for e in &self.table {
            write!(out, "{:#08x}, {:#08x}, ", e.lo, e.hi)?;
            match &e.mapping {
                EqMapping::IntegerOffset(o) => {
                    writeln!(out, "INTEGER_OFFSET, {},", *o)?;
                }
                EqMapping::Set(i) => {
                    writeln!(out, "DIRECT_MAPPING, {},", *i)?;
                }
                EqMapping::AlternatingAL => {
                    writeln!(out, "ALTERNATING_AL, 0,")?;
                }
                EqMapping::AlternatingUL => {
                    writeln!(out, "ALTERNATING_UL, 0,")?;
                }
                EqMapping::Single(v) => {
                    writeln!(out, "DIRECT_SINGLE, {},", *v)?;
                }
            }
        }
        writeln!(out, "}});")?;
        Ok(())
    }
}

fn list_to_ranges(set: &Vec<IElement>) -> Vec<IElement> {
    let mut ranges: Vec<IElement> = vec![];
    if set.len() > 0 {
        ranges.push(set[0]);
        let mut last = set[0];
        for v in set[1..].iter().cloned() {
            assert!(v >= last);
            if v != last + 1 {
                ranges.push(last);
                ranges.push(v);
            }
            last = v;
        }
        ranges.push(last);
    }
    return ranges;
}

fn list_to_ranges_str(set: &Vec<IElement>) -> String {
    list_to_ranges(set).iter().map(|v| format!("{:#08x}", v)).collect::<Vec<String>>().join(", ")
}

struct CollationMap<'a> {
    full_map: Vec<CollationElementIndex>,
    name: &'a str,
    equality: EqTable,
    equality_diff: Option<EqTable>,
    order: OrderTable,
    order_reverse: OrderTable,
    parent: Option<&'a CollationMap<'a>>,
}

impl CollationMap<'_> {
    /// Sorts a given list of collation elements with a given collator and creates lookup tables that allow
    ///  - looking up the sorting index of a given element (table `order`)
    ///  - looking up the element corresponding to a given sorting index (table `order_reverse`)
    ///  - looking up the set of elements that are considered equivalent to a given element (table `equality`)
    ///
    fn create<'a, F: Fn(&str, &str) -> Ordering>(collator: F, name: &'a str,
                                                 base_map: &'a Vec<CollationElementIndex>,
                                                 collation_elements: &Vec<CollationElement>,
                                                 parent: Option<&'a CollationMap<'a>>) -> CollationMap<'a> {
        let mut full_map: Vec<CollationElementIndex> = base_map.to_vec();
        // sort by initial index first, to keep the order of equal elements stable
        full_map.sort_by_key(|a| a.index_base);
        full_map.sort_by(|a, b| collator(&a.element.string, &b.element.string));

        let eq_table = EqTable::create(&collator, &full_map);
        let eq_diff = parent.map(|p| { EqTable::create_diff(&p.equality, &eq_table) });

        for i in 0..full_map.len() {
            full_map[i].index_src = full_map[i].index_dst;
            full_map[i].index_dst = i;
        }

        let table_dst_src = OrderTable::create(&full_map, |e| e.index_dst, |e| e.index_src);
        full_map.sort_by_key(|e| e.index_src);

        let table_src_dst = OrderTable::create(&full_map, |e| e.index_src, |e| e.index_dst);

        let map = CollationMap { name, full_map, equality: eq_table, equality_diff: eq_diff, order: table_src_dst, order_reverse: table_dst_src, parent };
        map.verify(&collator, collation_elements);
        map
    }

    fn equality_diff(&self) -> &EqTable {
        match &self.equality_diff {
            None => { &self.equality }
            Some(diff) => { diff }
        }
    }

    fn verify<F: Fn(&str, &str) -> Ordering>(&self, collator: F, collation_elements: &Vec<CollationElement>) {
        for e in &self.full_map {
            assert_eq!(self.order.lookup(e.index_src), e.index_dst, "elem: {:?}, table entry: {:?}", e, self.order.binary_search(e.index_src));
            assert_eq!(self.order_reverse.lookup(e.index_dst), e.index_src, "elem: {:?}, table entry: {:?}", e, self.order_reverse.binary_search(e.index_dst));
            self.equality_diff().lookup(e.index_base).map(|x| {
                for pair in x.windows(2) {
                    assert_eq!(collator(&collation_elements[pair[0]].string, &collation_elements[pair[1]].string), Ordering::Equal, "{:?} <=> {:?}",
                               collation_elements[pair[0]].string, collation_elements[pair[1]].string);
                }
            });
        }
    }

    fn dump(&self, path: &Path) -> std::io::Result<usize> {
        let mut file = File::create(path).expect("File open failed");
        file.write("TRGX".as_bytes())?;
        file.write(&FILE_FORMAT_VERSION.to_le_bytes())?;
        match self.parent {
            Some(parent) => {
                write_str(&mut file, parent.name)?;
            }
            None => {
                write_usize(&mut file, 0)?;
            }
        }
        write_usize(&mut file, self.equality_diff().sets.len())?;
        for e in &self.equality_diff().sets {
            write_usize(&mut file, e.len())?;
            for v in e {
                write_usize(&mut file, *v)?;
            }
        }
        write_usize(&mut file, self.equality_diff().table.len())?;
        for e in &self.equality_diff().table {
            write_usize(&mut file, e.lo)?;
            write_usize(&mut file, e.hi)?;
            match &e.mapping {
                EqMapping::IntegerOffset(o) => {
                    file.write(&[0])?;
                    write_i32(&mut file, *o)?;
                }
                EqMapping::Set(i) => {
                    file.write(&[1])?;
                    write_usize(&mut file, *i)?;
                }
                EqMapping::AlternatingAL => {
                    file.write(&[2])?;
                }
                EqMapping::AlternatingUL => {
                    file.write(&[3])?;
                }
                EqMapping::Single(v) => {
                    file.write(&[4])?;
                    write_usize(&mut file, *v)?;
                }
            }
        }
        Self::dump_order_table(&mut file, &self.order)?;
        Self::dump_order_table(&mut file, &self.order_reverse)
    }

    fn dump_order_table(mut file: &mut File, order_table: &OrderTable) -> std::io::Result<usize> {
        write_usize(&mut file, order_table.table.len())?;
        for e in &order_table.table {
            write_usize(&mut file, e.lo)?;
            write_usize(&mut file, e.hi)?;
            match &e.mapping {
                IntegerOffset(o) => {
                    file.write(&[0])?;
                    write_i32(&mut file, *o)?;
                }
                LUT(tbl) => {
                    file.write(&[1])?;
                    write_usize(&mut file, tbl.len())?;
                    for v in tbl {
                        write_usize(&mut file, *v)?;
                    }
                }
            }
        }
        Ok(0)
    }

    #[allow(dead_code)]
    fn print_size(&self) {
        println!("collation \"{}\":", self.name);
        self.equality_diff().print_size();
        self.order.print_size("order mapping");
        self.order_reverse.print_size("reverse order mapping");
        println!();
    }
}

fn write_str(file: &mut File, string: &str) -> std::io::Result<usize> {
    write_usize(file, string.len())?;
    file.write(string.as_bytes())
}

fn write_i32(file: &mut File, i: i32) -> std::io::Result<usize> {
    let bytes = &i.to_le_bytes();
    assert!(bytes[3] == 0 || bytes[3] == 0xff, "assumption broken: {:?} is larger than 0xff_ffff", i);
    file.write(&bytes[0..3])
}

fn write_usize(file: &mut File, i: usize) -> std::io::Result<usize> {
    assert!(i <= 0x7f_ffff, "assumption broken: {:?} is larger than 0x7f_ffff", i);
    file.write(&i.to_le_bytes()[0..3])
}

enum CollatorSetting {
    Default,
    CI,
    AI,
    CIAI,
}

impl Display for CollatorSetting {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", match self {
            CollatorSetting::Default => { "DEFAULT" }
            CollatorSetting::CI => { "CI" }
            CollatorSetting::AI => { "AI" }
            CollatorSetting::CIAI => { "CI_AI" }
        })
    }
}

fn get_collator_from_locale(locale: &Locale, collator_setting: &CollatorSetting) -> impl Fn(&str, &str) -> Ordering {
    let mut options = CollatorOptions::new();
    match collator_setting {
        CollatorSetting::Default => {
            options.strength = Some(Strength::Secondary);
            options.case_level = Some(CaseLevel::On);
        }
        CollatorSetting::CI => {
            options.strength = Some(Strength::Secondary);
            options.case_level = Some(CaseLevel::Off);
        }
        CollatorSetting::AI => {
            options.strength = Some(Strength::Primary);
            options.case_level = Some(CaseLevel::On);
        }
        CollatorSetting::CIAI => {
            options.strength = Some(Strength::Primary);
            options.case_level = Some(CaseLevel::Off);
        }
    }
    let collator: Collator = Collator::try_new(
        &locale.into(),
        options,
    ).unwrap();
    move |a: &str, b: &str| {
        collator.compare(a, b)
    }
}

#[allow(dead_code)]
fn dump_collation<'a, F: Fn(&str, &str) -> Ordering>(collator: F, folder: &Path, name: &'a str, collation_elements: &Vec<CollationElement>, parent_map: &'a CollationMap<'a>) -> CollationMap<'a> {
    let map = CollationMap::create(collator, name, &parent_map.full_map, &collation_elements, Some(&parent_map));
    map.dump(folder.join(format!("{}.trtbl", name).as_str()).as_path()).expect("file dump failed");
    map
}

#[allow(dead_code)]
fn dump_collations<'a>(base_map: &Vec<CollationElementIndex>, collation_elements: &Vec<CollationElement>) -> std::io::Result<()> {
    let time = Instant::now();

    for collator_setting in [CollatorSetting::Default, CollatorSetting::CI, CollatorSetting::AI, CollatorSetting::CIAI] {
        let folder = Path::new(OUTPUT_FOLDER).join(collator_setting.to_string());
        if !folder.exists() {
            std::fs::create_dir(folder.as_path())?;
        }
        let map_ducet = CollationMap::create(get_collator_from_locale(&Locale::default(), &collator_setting), "ducet", &base_map, &collation_elements, None);
        map_ducet.dump(folder.join("ducet.trtbl").as_path())?;
        println!("A -> {:?}", map_ducet.equality_diff().lookup(0x41).map(|m| m.iter().map(|x| char::from_u32(*x as u32).unwrap()).collect::<Vec<char>>()).unwrap_or(Vec::new()));
    }

    println!("done, took {:?}", time.elapsed());
    Ok(())
}

fn main() -> Result<()> {
    oracledb_start_docker_container();
    generate_case_fold_data()?;
    oracledb_generate_posix_char_classes()?;
    oracledb_generate_tests()?;
    Ok(())
}

fn generate_case_fold_data() -> Result<()> {
    let mut multi_character_strings: HashMap<String, IElement> = HashMap::new();

    let unicode_version = "15.1.0";
    let unicode_version_oracle_db = "12.1.0";
    let unicode_data_txt = fetch(format!("https://www.unicode.org/Public/{}/ucd/UnicodeData.txt", unicode_version))?;
    let unicode_case_folding_txt = fetch(format!("https://www.unicode.org/Public/{}/ucd/CaseFolding.txt", unicode_version))?;
    let unicode_case_folding_txt_oracle = fetch(format!("https://www.unicode.org/Public/{}/ucd/CaseFolding.txt", unicode_version_oracle_db))?;
    let unicode_special_casing = fetch(format!("https://www.unicode.org/Public/{}/ucd/SpecialCasing.txt", unicode_version))?;

    let eq_unicode_simple = unicode_case_folding(&unicode_case_folding_txt, &mut multi_character_strings, Simple)?;
    let eq_js_nu = js_non_unicode_case_folding(&unicode_data_txt, &unicode_special_casing, &mut multi_character_strings)?;
    let eq_python = python_unicode_case_folding(&unicode_data_txt, &unicode_special_casing, &mut multi_character_strings)?;
    let eq_ruby = unicode_case_folding_one_way(&unicode_case_folding_txt, &mut multi_character_strings, Full)?;
    let eq_oracle = unicode_case_folding_one_way(&unicode_case_folding_txt_oracle, &mut multi_character_strings, Full)?;
    let eq_oracle_ai = oracledb_extract_ai_case_fold_table(&mut multi_character_strings)?;
    let foldable_chars: Vec<IElement> = parse_case_folding_txt(&unicode_case_folding_txt, Simple)?.iter().map(|(src, _)| src.chars().next().unwrap() as IElement).collect();

    let mut out = vec![];
    writeln!(out)?;
    writeln!(out)?;

    writeln!(out, "public static final String[] MULTI_CHAR_SEQUENCES = {{")?;
    let mut strings_ordered = vec![""; multi_character_strings.len()];
    for (s, i) in multi_character_strings.iter() {
        strings_ordered[*i - 0x11_0000] = s.as_str();
    }
    for s in strings_ordered {
        writeln!(out, "\"{}\",", java_string_escape(s))?;
    }
    writeln!(out, "}};")?;
    let unicode_simple_name = format!("UNICODE_{}_SIMPLE", unicode_version.replace(".", "_"));
    let unicode_full_name = format!("UNICODE_{}_FULL", unicode_version.replace(".", "_"));
    eq_unicode_simple.dump_java(&mut out, unicode_simple_name.as_str(), None)?;
    EqTable::create_diff(&eq_unicode_simple, &eq_js_nu).dump_java(&mut out, "JS_NON_UNICODE", Some(unicode_simple_name.as_str()))?;
    EqTable::create_diff(&eq_unicode_simple, &eq_python).dump_java(&mut out, "PYTHON_UNICODE", Some(unicode_simple_name.as_str()))?;
    eq_ruby.dump_java_one_way(&mut out, unicode_full_name.as_str(), None)?;
    EqTable::create_diff(&eq_ruby, &eq_oracle).dump_java_one_way(&mut out, "ORACLE_DB", Some(unicode_full_name.as_str()))?;
    eq_oracle_ai.dump_java_one_way(&mut out, "ORACLE_DB_AI", None)?;
    writeln!(out, "public static final CodePointSet FOLDABLE_CHARACTERS = rangeSet({});", list_to_ranges_str(&foldable_chars))?;

    writeln!(out)?;
    insert_generated_code(Path::new(PATH_GRAAL_REPO).join(PATH_CASE_FOLD_DATA).as_path(), &out)?;
    Ok(())
}

fn java_string_escape(s: &str) -> String {
    s.chars().map(|c| {
        if c == '\\' {
            return "\\\\".to_string();
        }
        if c == '\n' {
            return "\\n".to_string();
        }
        if ' ' <= c && c <= '~' {
            return c.to_string();
        }
        let mut buf = [0; 2];
        return c.encode_utf16(&mut buf).iter().map(|v| format!("\\u{:04x}", v)).collect::<String>();
    }).collect::<String>()
}

fn insert_generated_code(path: &Path, code: &Vec<u8>) -> Result<()> {
    let file_content = fs::read_to_string(path)?;
    let pos_begin = file_content.find(GENERATED_CODE_MARKER_BEGIN).expect(format!("generated code begin marker not found in {}", path.to_str().unwrap()).as_str());
    let pos_end = file_content.find(GENERATED_CODE_MARKER_END).expect(format!("generated code end marker not found in {}", path.to_str().unwrap()).as_str());
    let mut f = File::create(path)?;
    f.write(file_content[0..pos_begin + GENERATED_CODE_MARKER_BEGIN.len()].as_bytes())?;
    f.write(code)?;
    f.write(file_content[pos_end..file_content.len()].as_bytes())?;
    Ok(())
}

fn fetch(url: String) -> Result<String> {
    println!("fetching {}", url);
    let path = Path::new("tmp").join(Path::new(&Url::parse(url.as_str()).unwrap().path()[1..]));
    fs::create_dir_all(path.parent().unwrap()).expect("mkdir failed");
    if path.exists() {
        return Ok(fs::read_to_string(path)?);
    }
    let body = reqwest::blocking::get(url)?.text()?;
    fs::write(path, &body).expect("write to download cache failed");
    Ok(body)
}

fn unicode_table(file: &String) -> Result<Reader<&[u8]>> {
    Ok(csv::ReaderBuilder::new().has_headers(false).delimiter(b';').comment(Some(b'#')).trim(Trim::All).flexible(true).from_reader(file.as_bytes()))
}

fn unicode_table_cell(record: &StringRecord, i: usize) -> String {
    parse_hex_chars(record.get(i).unwrap())
}

fn parse_hex_chars(s: &str) -> String {
    s.split(' ').map(|c| {
        char::from_u32(u32::from_str_radix(c, 16).unwrap()).unwrap()
    }).collect::<String>()
}

enum UnicodeCaseFoldingVariant {
    Simple,
    Full,
}

impl UnicodeCaseFoldingVariant {
    fn type_name(&self) -> &'static str {
        match self {
            Simple => { "S" }
            Full => { "F" }
        }
    }
}

fn parse_case_folding_txt(unicode_case_folding: &String, variant: UnicodeCaseFoldingVariant) -> Result<Vec<(String, String)>> {
    Ok(Vec::from_iter(unicode_table(unicode_case_folding)?.records().flat_map(|result| {
        let record = result.ok()?;
        let t = record.get(1).unwrap();
        if t == "C" || t == variant.type_name() {
            return Some((unicode_table_cell(&record, 0), unicode_table_cell(&record, 2)));
        }
        None
    })))
}

fn unicode_case_folding(unicode_case_folding: &String, multi_character_strings: &mut HashMap<String, IElement>, variant: UnicodeCaseFoldingVariant) -> Result<EqTable> {
    let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
    for (src, dst) in parse_case_folding_txt(unicode_case_folding, variant)? {
        eq_builder.add_equivalence(src.as_str(), dst.as_str());
    }
    Ok(eq_builder.create_eq_table())
}

fn unicode_case_folding_one_way(unicode_case_folding: &String, multi_character_strings: &mut HashMap<String, IElement>, variant: UnicodeCaseFoldingVariant) -> Result<EqTable> {
    let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
    let mut mappings: Vec<(IElement, IElement)> = vec![];
    for (src, dst) in parse_case_folding_txt(unicode_case_folding, variant)? {
        mappings.push((eq_builder.index(src.as_str()), eq_builder.index(dst.as_str())));
    }
    Ok(EqTable::create_one_way_mapping(mappings))
}

fn js_non_unicode_case_folding(unicode_data: &String, unicode_special_casing: &String, multi_character_strings: &mut HashMap<String, IElement>) -> Result<EqTable> {
    let mut upper_map: HashMap<String, String> = HashMap::new();
    for result in unicode_table(unicode_data)?.records() {
        let record = result?;
        if record.get(12).unwrap() == "" {
            // Drop entries without toUppercase mapping
            continue;
        }
        upper_map.insert(unicode_table_cell(&record, 0), unicode_table_cell(&record, 12));
    }
    for result in unicode_table(unicode_special_casing)?.records() {
        let record = result?;
        if record.len() > 5 {
            // Drop entries with conditions
            continue;
        }
        upper_map.insert(unicode_table_cell(&record, 0), unicode_table_cell(&record, 3));
    }
    let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
    for (chr, upper) in upper_map {
        let c = chr.chars().next().unwrap();
        let u = upper.chars().next().unwrap();
        if upper.chars().count() > 1 || u >= '\u{10000}' {
            // Only follow rules which give map to a single UTF-16 code unit
            continue;
        }
        if c > '\u{7f}' && u <= '\u{7f}' {
            // Do not allow non-ASCII characters to cross into ASCII.
            continue;
        }
        if c == u {
            // Drop trivial mappings
            continue;
        }
        eq_builder.add_equivalence(chr.as_str(), upper.as_str());
    }
    Ok(eq_builder.create_eq_table())
}

fn python_unicode_case_folding(unicode_data: &String, unicode_special_casing: &String, multi_character_strings: &mut HashMap<String, IElement>) -> Result<EqTable> {
    fn read_data_file_mapping(unicode_data_file: &String, multi_character_strings: &mut HashMap<String, IElement>, cell_src: usize, cell_dst: usize) -> Result<HashMap<IElement, Vec<IElement>>> {
        let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
        for result in unicode_table(unicode_data_file)?.records() {
            let record = result?;
            let dst = record.get(cell_dst).unwrap();
            if dst != "" {
                eq_builder.add_equivalence(unicode_table_cell(&record, cell_src).as_str(), parse_hex_chars(dst).as_str());
            }
        }
        Ok(eq_builder.equivalences)
    }

    fn read_special_casing_mapping(unicode_special_casing: &String, multi_character_strings: &mut HashMap<String, IElement>, cell_src: usize, cell_dst: usize) -> Result<HashMap<IElement, Vec<IElement>>> {
        let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
        for result in unicode_table(unicode_special_casing)?.records() {
            let record = result?;
            if record.len() > 5 {
                // Drop entries with conditions
                continue;
            }
            let c = unicode_table_cell(&record, cell_src);
            let dst = unicode_table_cell(&record, cell_dst);
            if dst.chars().count() > 1 {
                eq_builder.add_equivalence_src_only(c.as_str(), dst.as_str());
            }
        }
        Ok(eq_builder.equivalences)
    }

    let eq_lower = read_data_file_mapping(unicode_data, multi_character_strings, 0, 12)?;
    let eq_upper = read_data_file_mapping(unicode_data, multi_character_strings, 0, 13)?;
    let eq_special_lower = read_special_casing_mapping(unicode_special_casing, multi_character_strings, 0, 1)?;
    let eq_special_upper = read_special_casing_mapping(unicode_special_casing, multi_character_strings, 0, 3)?;
    let merged = Vec::from_iter(merge_eq_classes(merge_eq_classes(eq_lower.values(), eq_special_lower.values()).iter(), merge_eq_classes(eq_upper.values(), eq_special_upper.values()).iter()).iter().map(|set| Vec::from_iter(set.iter().cloned())));
    Ok(EqTable::from_vec(merged))
}

struct EquivalenceBuilder<'a> {
    multi_character_strings: &'a mut HashMap<String, IElement>,
    equivalences: HashMap<IElement, Vec<IElement>>,
}

impl EquivalenceBuilder<'_> {
    fn new(multi_character_strings: &mut HashMap<String, IElement>) -> EquivalenceBuilder {
        EquivalenceBuilder { multi_character_strings, equivalences: Default::default() }
    }

    fn index(&mut self, s: &str) -> IElement {
        if s.chars().count() == 1 {
            return s.chars().next().unwrap() as IElement;
        }
        let next_id = self.multi_character_strings.len() + 0x11_0000;
        return *self.multi_character_strings.entry(s.to_string()).or_insert(next_id);
    }

    fn add_equivalence(&mut self, a: &str, b: &str) {
        let i = self.index(a);
        let j = self.index(b);
        let buf = self.equivalences.entry(j).or_default();
        if buf.len() == 0 {
            buf.push(j);
        }
        buf.push(i);
    }

    fn add_equivalence_src_only(&mut self, a: &str, b: &str) {
        let i = self.index(a);
        let j = self.index(b);
        self.equivalences.entry(j).or_default().push(i);
    }

    fn create_eq_table(&mut self) -> EqTable {
        EqTable::from_vec(Vec::from_iter(self.equivalences.values().cloned()))
    }
}

fn merge_eq_classes<'a, I, Inner>(a: I, b: I) -> Vec<HashSet<IElement>> where I: Iterator<Item=Inner>, Inner: IntoIterator<Item=&'a IElement> + Copy + Debug {
    let eq_classes_a: Vec<HashSet<IElement>> = Vec::from_iter(a.map(|eq_class_a| HashSet::from_iter(eq_class_a.into_iter().cloned())));
    let chars_a_mapped_to_class_index: HashMap<IElement, usize> = HashMap::from_iter(eq_classes_a.iter().enumerate().flat_map(|(i, set)| {
        set.iter().cloned().map(move |v| (v, i))
    }));
    let mut eq_class_a_copy = vec![true; eq_classes_a.len()];
    let mut merged_classes: Vec<HashSet<IElement>> = b.map(|eq_class_b| {
        HashSet::from_iter(eq_class_b.into_iter().flat_map(|char_b: &IElement| chars_a_mapped_to_class_index.get(char_b)).flat_map(|i| {
            eq_class_a_copy[*i] = false;
            eq_classes_a.get(*i)
        }).flatten().cloned().chain(eq_class_b.into_iter().cloned()))
    }).collect();
    for (i, copy) in eq_class_a_copy.iter().enumerate() {
        if *copy {
            merged_classes.push(eq_classes_a.get(i).unwrap().clone());
        }
    }
    merged_classes
}

fn oracledb_start_docker_container() {
    if String::from_utf8(Command::new("docker").args(["container", "ls", "--filter", "name=oracle-db", "--format", "{{json .Names}}"]).output().expect("docker ls failed").stdout).expect("could not decode output of 'docker ls'").trim() == "\"oracle-db\"" {
        return;
    }
    if String::from_utf8(Command::new("docker").args(["container", "ls", "-a", "--filter", "name=oracle-db", "--format", "{{json .Names}}"]).output().expect("docker ls failed").stdout).expect("could not decode output of 'docker ls'").trim() != "\"oracle-db\"" {
        Command::new("docker").args(["run", "-d", "--name", "oracle-db", "-p", "1521:1521", "-p", "5500:5500", "-e", "ORACLE_PWD=passwd", "container-registry.oracle.com/database/express:21.3.0-xe"]).output().expect("docker run failed");
    }
    let docker_start = Command::new("docker").args(["start", "oracle-db"]).output().expect("docker start failed");
    if docker_start.status.code().unwrap() != 0 {
        println!("{}", String::from_utf8(docker_start.stderr).unwrap());
        panic!("docker start failed");
    }
    // wait for db startup
    std::thread::sleep(std::time::Duration::from_secs(8));
}

fn oracledb_connect() -> std::result::Result<Connection, oracle::Error> {
    Connector::new("sys", "passwd", "//localhost/XE").privilege(Privilege::Sysdba).connect().map_err(|error| {
        match &error {
            oracle::Error::OciError(db_error) => {
                if db_error.code() == 12637 {
                    println!("Could not connect to docker container, you may have to add {{ \"userland-proxy\": false }} to /etc/docker/daemon.json");
                    println!("see https://franckpachot.medium.com/19c-instant-client-and-docker-1566630ab20e");
                }
                error
            }
            _ => error
        }
    })
}

fn oracledb_extract_ai_case_fold_table<'a>(multi_character_strings: &mut HashMap<String, IElement>) -> Result<EqTable> {
    let conn = oracledb_connect()?;

    let mut eq_builder = EquivalenceBuilder::new(multi_character_strings);
    let mut mappings: Vec<(IElement, IElement)> = vec![];

    let query = "select nlssort(:c, 'nls_sort = binary_ai') from dual";
    println!("extracting accent insensitive mappings from OracleDB");
    let mut statement = conn.statement(query).build()?;
    for s in (0u32..0xd800).chain(0xe000..0x110000).map(|i| String::from(char::from_u32(i).unwrap())).progress_count(0xd800 + (0x110000 - 0xe000)) {
        assert_eq!(s.chars().count(), 1);
        let base_chars_bytes = statement.query_row_as::<Vec<u8>>(&[&s]).unwrap();
        let base_chars_u16: Vec<u16> = base_chars_bytes.chunks_exact(2).into_iter().map(|a| u16::from_le_bytes([a[1], a[0]])).collect();
        let base_chars = String::from_utf16(base_chars_u16.as_slice()).unwrap();
        if base_chars != s {
            mappings.push((eq_builder.index(s.as_str()), eq_builder.index(base_chars.as_str())));
        }
    }
    Ok(EqTable::create_one_way_mapping(mappings))
}

fn oracledb_create_chars_table(conn: &Connection) -> Result<()> {
    match conn.query("select * from chars where v = 0", &[]) {
        Ok(_) => {
            Ok(())
        }
        Err(oracle::Error::OciError(db_error)) if db_error.code() == 942 => {
            // table does not exist
            conn.execute("create table chars(v int, c varchar2(32))", &[])?;
            let query = "insert into chars(v, c) values (:v, :c)";
            let mut statement = conn.batch(query, 0x1000).build()?;
            for i in (0u32..0xd800).chain(0xe000..0x110000) {
                statement.append_row(&[&i, &String::from(char::from_u32(i).unwrap())])?;
            }
            statement.execute()?;
            conn.commit()?;
            Ok(())
        }
        Err(e) => Err(e.into())
    }
}

fn oracledb_generate_posix_char_classes() -> Result<()> {
    let conn = oracledb_connect()?;
    oracledb_create_chars_table(&conn)?;
    let query = "SELECT v from chars WHERE REGEXP_LIKE(c, :r, '') ORDER BY v";
    let mut statement = conn.statement(query).build()?;
    let mut out = vec![];
    for name in ["alpha", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "xdigit"] {
        let mut chars: Vec<IElement> = vec![];
        for row_result in statement.query_as::<IElement>(&[&format!("[[:{}:]]", name).as_str()])? {
            chars.push(row_result?);
        }
        writeln!(out, "\n\nPOSIX_CHAR_CLASSES.put(\"{}\", CodePointSet.createNoDedup(", name)?;
        writeln!(out, "{}));\n", list_to_ranges_str(&chars))?;
    }
    insert_generated_code(Path::new(PATH_GRAAL_REPO).join(PATH_ORACLE_DB_CONSTANTS).as_path(), &out)?;
    Ok(())
}

fn oracledb_generate_tests() -> Result<()> {
    enum TestResult {
        Match(Vec<i32>),
        NoMatch,
        SyntaxError(String),
    }

    fn run_test(statement: &mut Statement, pattern: &str, flags: &str, input: &str, from_index: i32) -> Result<TestResult> {
        fn count_groups(pattern: &str) -> i32 {
            let mut par_open = 0;
            let mut escaped = false;
            let mut n = 1;
            for c in pattern.chars() {
                if !escaped {
                    if c == '(' {
                        par_open += 1;
                    } else if c == ')' {
                        if par_open > 0 {
                            par_open -= 1;
                            n += 1;
                        }
                    }
                }
                escaped = c == '\\';
            }
            return min(n, 10);
        }
        let occurrence = 1;
        let n_groups = count_groups(pattern);
        let mut groups = vec![];
        for i_group in 0..n_groups {
            for start_or_end in [0, 1] {
                // explicit type for flags string: the client library will set the data type of strings to NVARCHAR2, but REGEXP_INSTR only accepts VARCHAR or CHAR on the flags parameter
                match statement.query_row_as::<i32>(&[&input, &pattern, &from_index, &occurrence, &start_or_end, &(&flags, &OracleType::Char(10)), &i_group]) {
                    Ok(i) => {
                        if i_group == 0 && i == 0 {
                            return Ok(TestResult::NoMatch);
                        }
                        groups.push(i - 1);
                    }
                    Err(oracle::Error::OciError(e)) => {
                        return Ok(TestResult::SyntaxError(e.message()[(e.message().find(": ").unwrap() + 2)..].to_string()));
                    }
                    Err(e) => {
                        bail!(e);
                    }
                }
            }
        }
        return Ok(TestResult::Match(groups));
    }

    let conn = oracledb_connect()?;
    let query = "SELECT REGEXP_INSTR(:input, :pattern, :fromIndex, :occurrence, :startOrEnd, :flags, :iGroup) from dual";
    let mut statement = conn.statement(query).build()?;
    let mut out = vec![];
    writeln!(out)?;
    for (pattern, flags, input) in [
        ("abracadabra$", "", "abracadabracadabra"),
        ("a...b", "", "abababbb"),
        ("XXXXXX", "", "..XXXXXX"),
        ("\\)", "", "()"),
        ("a]", "", "a]a"),
        ("}", "", "}"),
        ("\\}", "", "}"),
        ("\\]", "", "]"),
        ("]", "", "]"),
        ("]", "", "]"),
        ("{", "", "{"),
        ("}", "", "}"),
        ("^a", "", "ax"),
        ("\\^a", "", "a^a"),
        ("a\\^", "", "a^"),
        ("a$", "", "aa"),
        ("a\\$", "", "a$"),
        ("a($)", "", "aa"),
        ("a*(^a)", "", "aa"),
        ("(..)*(...)*", "", "a"),
        ("(..)*(...)*", "", "abcd"),
        ("(ab|a)(bc|c)", "", "abc"),
        ("(ab)c|abc", "", "abc"),
        ("a{0}b", "", "ab"),
        ("(a*)(b?)(b+)b{3}", "", "aaabbbbbbb"),
        ("(a*)(b{0,1})(b{1,})b{3}", "", "aaabbbbbbb"),
        ("a{9876543210}", "", "a"),
        ("((a|a)|a)", "", "a"),
        ("(a*)(a|aa)", "", "aaaa"),
        ("a*(a.|aa)", "", "aaaa"),
        ("a(b)|c(d)|a(e)f", "", "aef"),
        ("(a|b)?.*", "", "b"),
        ("(a|b)c|a(b|c)", "", "ac"),
        ("(a|b)c|a(b|c)", "", "ab"),
        ("(a|b)*c|(a|ab)*c", "", "abc"),
        ("(a|b)*c|(a|ab)*c", "", "xc"),
        ("(.a|.b).*|.*(.a|.b)", "", "xa"),
        ("a?(ab|ba)ab", "", "abab"),
        ("a?(ac{0}b|ba)ab", "", "abab"),
        ("ab|abab", "", "abbabab"),
        ("aba|bab|bba", "", "baaabbbaba"),
        ("aba|bab", "", "baaabbbaba"),
        ("(aa|aaa)*|(a|aaaaa)", "", "aa"),
        ("(a.|.a.)*|(a|.a...)", "", "aa"),
        ("ab|a", "", "xabc"),
        ("ab|a", "", "xxabc"),
        ("(Ab|cD)*", "", "aBcD"),
        ("[^-]", "", "--a"),
        ("[a-]*", "", "--a"),
        ("[a-m-]*", "", "--amoma--"),
        (":::1:::0:|:::1:1:0:", "", ":::0:::1:::1:::0:"),
        (":::1:::0:|:::1:1:1:", "", ":::0:::1:::1:::0:"),
        ("[[:upper:]]", "", "A"),
        ("[[:lower:]]+", "", "`az{"),
        ("[[:upper:]]+", "", "@AZ["),
        ("[[-]]", "", "[[-]]"),
        ("\\n", "", "\\n"),
        ("\\n", "", "\\n"),
        ("[^a]", "", "\\n"),
        ("\\na", "", "\\na"),
        ("(a)(b)(c)", "", "abc"),
        ("xxx", "", "xxx"),
        ("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "feb 6,"),
        ("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "2/7"),
        ("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "feb 1,Feb 6"),
        ("((((((((((((((((((((((((((((((x))))))))))))))))))))))))))))))", "", "x"),
        ("((((((((((((((((((((((((((((((x))))))))))))))))))))))))))))))*", "", "xx"),
        ("a?(ab|ba)*", "", "ababababababababababababababababababababababababababababababababababababababababa"),
        ("abaa|abbaa|abbbaa|abbbbaa", "", "ababbabbbabbbabbbbabbbbaa"),
        ("abaa|abbaa|abbbaa|abbbbaa", "", "ababbabbbabbbabbbbabaa"),
        ("aaac|aabc|abac|abbc|baac|babc|bbac|bbbc", "", "baaabbbabac"),
        (".*", "", "\\x01\\xff"),
        ("aaaa|bbbb|cccc|ddddd|eeeeee|fffffff|gggg|hhhh|iiiii|jjjjj|kkkkk|llll", "", "XaaaXbbbXcccXdddXeeeXfffXgggXhhhXiiiXjjjXkkkXlllXcbaXaaaa"),
        ("aaaa\\nbbbb\\ncccc\\nddddd\\neeeeee\\nfffffff\\ngggg\\nhhhh\\niiiii\\njjjjj\\nkkkkk\\nllll", "", "XaaaXbbbXcccXdddXeeeXfffXgggXhhhXiiiXjjjXkkkXlllXcbaXaaaa"),
        ("a*a*a*a*a*b", "", "aaaaaaaaab"),
        ("^", "", "a"),
        ("$", "", "a"),
        ("^$", "", "a"),
        ("^a$", "", "a"),
        ("abc", "", "abc"),
        ("abc", "", "xabcy"),
        ("abc", "", "ababc"),
        ("ab*c", "", "abc"),
        ("ab*bc", "", "abc"),
        ("ab*bc", "", "abbc"),
        ("ab*bc", "", "abbbbc"),
        ("ab+bc", "", "abbc"),
        ("ab+bc", "", "abbbbc"),
        ("ab?bc", "", "abbc"),
        ("ab?bc", "", "abc"),
        ("ab?c", "", "abc"),
        ("^abc$", "", "abc"),
        ("^abc", "", "abcc"),
        ("abc$", "", "aabc"),
        ("^", "", "abc"),
        ("$", "", "abc"),
        ("a.c", "", "abc"),
        ("a.c", "", "axc"),
        ("a.*c", "", "axyzc"),
        ("a[bc]d", "", "abd"),
        ("a[b-d]e", "", "ace"),
        ("a[b-d]", "", "aac"),
        ("a[-b]", "", "a-"),
        ("a[b-]", "", "a-"),
        ("a]", "", "a]"),
        ("a[]]b", "", "a]b"),
        ("a[^bc]d", "", "aed"),
        ("a[^-b]c", "", "adc"),
        ("a[^]b]c", "", "adc"),
        ("ab|cd", "", "abc"),
        ("ab|cd", "", "abcd"),
        ("a\\(b", "", "a(b"),
        ("a\\(*b", "", "ab"),
        ("a\\(*b", "", "a((b"),
        ("((a))", "", "abc"),
        ("(a)b(c)", "", "abc"),
        ("a+b+c", "", "aabbabc"),
        ("a*", "", "aaa"),
        ("(a*)*", "", "-"),
        ("(a*)+", "", "-"),
        ("(a*|b)*", "", "-"),
        ("(a+|b)*", "", "ab"),
        ("(a+|b)+", "", "ab"),
        ("(a+|b)?", "", "ab"),
        ("[^ab]*", "", "cde"),
        ("(^)*", "", "-"),
        ("a*", "", "a"),
        ("([abc])*d", "", "abbbcd"),
        ("([abc])*bcd", "", "abcd"),
        ("a|b|c|d|e", "", "e"),
        ("(a|b|c|d|e)f", "", "ef"),
        ("((a*|b))*", "", "-"),
        ("abcd*efg", "", "abcdefg"),
        ("ab*", "", "xabyabbbz"),
        ("ab*", "", "xayabbbz"),
        ("(ab|cd)e", "", "abcde"),
        ("[abhgefdc]ij", "", "hij"),
        ("(a|b)c*d", "", "abcd"),
        ("(ab|ab*)bc", "", "abc"),
        ("a([bc]*)c*", "", "abc"),
        ("a([bc]*)(c*d)", "", "abcd"),
        ("a([bc]+)(c*d)", "", "abcd"),
        ("a([bc]*)(c+d)", "", "abcd"),
        ("a[bcd]*dcdcde", "", "adcdcde"),
        ("(ab|a)b*c", "", "abc"),
        ("((a)(b)c)(d)", "", "abcd"),
        ("[A-Za-z_][A-Za-z0-9_]*", "", "alpha"),
        ("^a(bc+|b[eh])g|.h$", "", "abh"),
        ("(bc+d$|ef*g.|h?i(j|k))", "", "effgz"),
        ("(bc+d$|ef*g.|h?i(j|k))", "", "ij"),
        ("(bc+d$|ef*g.|h?i(j|k))", "", "reffgz"),
        ("(((((((((a)))))))))", "", "a"),
        ("multiple words", "", "multiple words yeah"),
        ("(.*)c(.*)", "", "abcde"),
        ("abcd", "", "abcd"),
        ("a(bc)d", "", "abcd"),
        ("a[\u{0001}-\u{0003}]?c", "", "a\u{0002}c"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Qaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mo'ammar Gadhafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Kaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Qadhafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Gadafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mu'ammar Qadafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moamar Gaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mu'ammar Qadhdhafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Khaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghaddafy"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghadafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muamar Kaddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Quathafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Gheddafi"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moammar Khadafy"),
        ("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moammar Qudhafi"),
        ("a+(b|c)*d+", "", "aabcdd"),
        ("^.+$", "", "vivi"),
        ("^(.+)$", "", "vivi"),
        ("^([^!.]+).att.com!(.+)$", "", "gryphon.att.com!eby"),
        ("^([^!]+!)?([^!]+)$", "", "bas"),
        ("^([^!]+!)?([^!]+)$", "", "bar!bas"),
        ("^([^!]+!)?([^!]+)$", "", "foo!bas"),
        ("^.+!([^!]+!)([^!]+)$", "", "foo!bar!bas"),
        ("((foo)|(bar))!bas", "", "bar!bas"),
        ("((foo)|(bar))!bas", "", "foo!bar!bas"),
        ("((foo)|(bar))!bas", "", "foo!bas"),
        ("((foo)|bar)!bas", "", "bar!bas"),
        ("((foo)|bar)!bas", "", "foo!bar!bas"),
        ("((foo)|bar)!bas", "", "foo!bas"),
        ("(foo|(bar))!bas", "", "bar!bas"),
        ("(foo|(bar))!bas", "", "foo!bar!bas"),
        ("(foo|(bar))!bas", "", "foo!bas"),
        ("(foo|bar)!bas", "", "bar!bas"),
        ("(foo|bar)!bas", "", "foo!bar!bas"),
        ("(foo|bar)!bas", "", "foo!bas"),
        ("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bar!bas"),
        ("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "bas"),
        ("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "bar!bas"),
        ("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "foo!bar!bas"),
        ("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "foo!bas"),
        ("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "bas"),
        ("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "bar!bas"),
        ("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bar!bas"),
        ("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bas"),
        (".*(/XXX).*", "", "/XXX"),
        (".*(\\\\XXX).*", "", "\\XXX"),
        ("\\\\XXX", "", "\\XXX"),
        (".*(/000).*", "", "/000"),
        (".*(\\\\000).*", "", "\\000"),
        ("\\\\000", "", "\\000"),
        ("aa*", "", "xaxaax"),
        ("(a*)(ab)*(b*)", "", "abc"),
        ("(a*)(ab)*(b*)", "", "abc"),
        ("((a*)(ab)*)((b*)(a*))", "", "aba"),
        ("((a*)(ab)*)((b*)(a*))", "", "aba"),
        ("(...?.?)*", "", "xxxxxx"),
        ("(...?.?)*", "", "xxxxxx"),
        ("(...?.?)*", "", "xxxxxx"),
        ("(a|ab)(bc|c)", "", "abcabc"),
        ("(a|ab)(bc|c)", "", "abcabc"),
        ("(aba|a*b)(aba|a*b)", "", "ababa"),
        ("(aba|a*b)(aba|a*b)", "", "ababa"),
        ("a(b)*\\1", "", "a"),
        ("a(b)*\\1", "", "a"),
        ("a(b)*\\1", "", "abab"),
        ("(a*){2}", "", "xxxxx"),
        ("(a*){2}", "", "xxxxx"),
        ("a(b)*\\1", "", "abab"),
        ("a(b)*\\1", "", "abab"),
        ("a(b)*\\1", "", "abab"),
        ("(a*)*", "", "a"),
        ("(a*)*", "", "ax"),
        ("(a*)*", "", "a"),
        ("(aba|a*b)*", "", "ababa"),
        ("(aba|a*b)*", "", "ababa"),
        ("(aba|a*b)*", "", "ababa"),
        ("(a(b)?)+", "", "aba"),
        ("(a(b)?)+", "", "aba"),
        ("(a(b)*)*\\2", "", "abab"),
        ("(a(b)*)*\\2", "", "abab"),
        ("(a?)((ab)?)(b?)a?(ab)?b?", "", "abab"),
        (".*(.*)", "", "ab"),
        (".*(.*)", "", "ab"),
        ("(a|ab)(c|bcd)", "", "abcd"),
        ("(a|ab)(bcd|c)", "", "abcd"),
        ("(ab|a)(c|bcd)", "", "abcd"),
        ("(ab|a)(bcd|c)", "", "abcd"),
        ("((a|ab)(c|bcd))(d*)", "", "abcd"),
        ("((a|ab)(bcd|c))(d*)", "", "abcd"),
        ("((ab|a)(c|bcd))(d*)", "", "abcd"),
        ("((ab|a)(bcd|c))(d*)", "", "abcd"),
        ("(a|ab)((c|bcd)(d*))", "", "abcd"),
        ("(a|ab)((bcd|c)(d*))", "", "abcd"),
        ("(ab|a)((c|bcd)(d*))", "", "abcd"),
        ("(ab|a)((bcd|c)(d*))", "", "abcd"),
        ("(a*)(b|abc)", "", "abc"),
        ("(a*)(abc|b)", "", "abc"),
        ("((a*)(b|abc))(c*)", "", "abc"),
        ("((a*)(abc|b))(c*)", "", "abc"),
        ("(a*)((b|abc)(c*))", "", "abc"),
        ("(a*)((abc|b)(c*))", "", "abc"),
        ("(a*)(b|abc)", "", "abc"),
        ("(a*)(abc|b)", "", "abc"),
        ("((a*)(b|abc))(c*)", "", "abc"),
        ("((a*)(abc|b))(c*)", "", "abc"),
        ("(a*)((b|abc)(c*))", "", "abc"),
        ("(a*)((abc|b)(c*))", "", "abc"),
        ("(a|ab)", "", "ab"),
        ("(ab|a)", "", "ab"),
        ("(a|ab)(b*)", "", "ab"),
        ("(ab|a)(b*)", "", "ab"),
        ("a+", "", "xaax"),
        (".(a*).", "", "xaax"),
        ("(a?)((ab)?)", "", "ab"),
        ("(a?)((ab)?)(b?)", "", "ab"),
        ("((a?)((ab)?))(b?)", "", "ab"),
        ("(a?)(((ab)?)(b?))", "", "ab"),
        ("(.?)", "", "x"),
        ("(.?){1}", "", "x"),
        ("(.?)(.?)", "", "x"),
        ("(.?){2}", "", "x"),
        ("(.?)*", "", "x"),
        ("(.?.?)", "", "xxx"),
        ("(.?.?){1}", "", "xxx"),
        ("(.?.?)(.?.?)", "", "xxx"),
        ("(.?.?){2}", "", "xxx"),
        ("(.?.?)(.?.?)(.?.?)", "", "xxx"),
        ("(.?.?){3}", "", "xxx"),
        ("(.?.?)*", "", "xxx"),
        ("a?((ab)?)(b?)", "", "ab"),
        ("(a?)((ab)?)b?", "", "ab"),
        ("a?((ab)?)b?", "", "ab"),
        ("(a*){2}", "", "xxxxx"),
        ("(ab?)(b?a)", "", "aba"),
        ("(a|ab)(ba|a)", "", "aba"),
        ("(a|ab|ba)", "", "aba"),
        ("(a|ab|ba)(a|ab|ba)", "", "aba"),
        ("(a|ab|ba)*", "", "aba"),
        ("(aba|a*b)", "", "ababa"),
        ("(aba|a*b)(aba|a*b)", "", "ababa"),
        ("(aba|a*b)*", "", "ababa"),
        ("(aba|ab|a)", "", "ababa"),
        ("(aba|ab|a)(aba|ab|a)", "", "ababa"),
        ("(aba|ab|a)*", "", "ababa"),
        ("(a(b)?)", "", "aba"),
        ("(a(b)?)(a(b)?)", "", "aba"),
        ("(a(b)?)+", "", "aba"),
        ("(.*)(.*)", "", "xx"),
        (".*(.*)", "", "xx"),
        ("(a.*z|b.*y)", "", "azbazby"),
        ("(a.*z|b.*y)(a.*z|b.*y)", "", "azbazby"),
        ("(a.*z|b.*y)*", "", "azbazby"),
        ("(.|..)(.*)", "", "ab"),
        ("((..)*(...)*)", "", "xxx"),
        ("((..)*(...)*)((..)*(...)*)", "", "xxx"),
        ("((..)*(...)*)*", "", "xxx"),
        ("(a{0,1})*b\\1", "", "ab"),
        ("(a*)*b\\1", "", "ab"),
        ("(a*)b\\1*", "", "ab"),
        ("(a*)*b\\1*", "", "ab"),
        ("(a{0,1})*b(\\1)", "", "ab"),
        ("(a*)*b(\\1)", "", "ab"),
        ("(a*)b(\\1)*", "", "ab"),
        ("(a*)*b(\\1)*", "", "ab"),
        ("(a{0,1})*b\\1", "", "aba"),
        ("(a*)*b\\1", "", "aba"),
        ("(a*)b\\1*", "", "aba"),
        ("(a*)*b\\1*", "", "aba"),
        ("(a*)*b(\\1)*", "", "aba"),
        ("(a{0,1})*b\\1", "", "abaa"),
        ("(a*)*b\\1", "", "abaa"),
        ("(a*)b\\1*", "", "abaa"),
        ("(a*)*b\\1*", "", "abaa"),
        ("(a*)*b(\\1)*", "", "abaa"),
        // ("(a{0,1})*b\\1", "", "aab"), LXR bug
        ("(a*)*b\\1", "", "aab"),
        ("(a*)b\\1*", "", "aab"),
        ("(a*)*b\\1*", "", "aab"),
        ("(a*)*b(\\1)*", "", "aab"),
        // ("(a{0,1})*b\\1", "", "aaba"), LXR bug
        ("(a*)*b\\1", "", "aaba"),
        ("(a*)b\\1*", "", "aaba"),
        ("(a*)*b\\1*", "", "aaba"),
        ("(a*)*b(\\1)*", "", "aaba"),
        // ("(a{0,1})*b\\1", "", "aabaa"), LXR bug
        ("(a*)*b\\1", "", "aabaa"),
        ("(a*)b\\1*", "", "aabaa"),
        ("(a*)*b\\1*", "", "aabaa"),
        ("(a*)*b(\\1)*", "", "aabaa"),
        ("(x)*a\\1", "", "a"),
        ("(x)*a\\1*", "", "a"),
        ("(x)*a(\\1)", "", "a"),
        ("(x)*a(\\1)*", "", "a"),
        ("(aa(b(b))?)+", "", "aabbaa"),
        ("(a(b)?)+", "", "aba"),
        ("([ab]+)([bc]+)([cd]*)", "", "abcd"),
        ("([ab]*)([bc]*)([cd]*)\\1", "", "abcdaa"),
        ("([ab]*)([bc]*)([cd]*)\\1", "", "abcdab"),
        ("([ab]*)([bc]*)([cd]*)\\1*", "", "abcdaa"),
        ("([ab]*)([bc]*)([cd]*)\\1*", "", "abcdab"),
        ("^(A([^B]*))?(B(.*))?", "", "Aa"),
        ("^(A([^B]*))?(B(.*))?", "", "Bb"),
        (".*([AB]).*\\1", "", "ABA"),
        ("[^A]*A", "", "\\nA"),
        ("(a|ab)(c|bcd)(d*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d*)", "", "abcd"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a|ab)(c|bcd)(d|.*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d|.*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d|.*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d|.*)", "", "abcd"),
        ("(a*)*", "", "a"),
        ("(a*)*", "", "x"),
        ("(a*)*", "", "aaaaaa"),
        ("(a*)*", "", "aaaaaax"),
        ("(a*)+", "", "a"),
        ("(a*)+", "", "x"),
        ("(a*)+", "", "aaaaaa"),
        ("(a*)+", "", "aaaaaax"),
        ("(a+)*", "", "a"),
        ("(a+)*", "", "x"),
        ("(a+)*", "", "aaaaaa"),
        ("(a+)*", "", "aaaaaax"),
        ("(a+)+", "", "a"),
        ("(a+)+", "", "x"),
        ("(a+)+", "", "aaaaaa"),
        ("(a+)+", "", "aaaaaax"),
        ("([a]*)*", "", "a"),
        ("([a]*)*", "", "x"),
        ("([a]*)*", "", "aaaaaa"),
        ("([a]*)*", "", "aaaaaax"),
        ("([a]*)+", "", "a"),
        ("([a]*)+", "", "x"),
        ("([a]*)+", "", "aaaaaa"),
        ("([a]*)+", "", "aaaaaax"),
        ("([^b]*)*", "", "a"),
        ("([^b]*)*", "", "b"),
        ("([^b]*)*", "", "aaaaaa"),
        ("([^b]*)*", "", "aaaaaab"),
        ("([ab]*)*", "", "a"),
        ("([ab]*)*", "", "aaaaaa"),
        ("([ab]*)*", "", "ababab"),
        ("([ab]*)*", "", "bababa"),
        ("([ab]*)*", "", "b"),
        ("([ab]*)*", "", "bbbbbb"),
        ("([ab]*)*", "", "aaaabcde"),
        ("([^a]*)*", "", "b"),
        ("([^a]*)*", "", "bbbbbb"),
        ("([^a]*)*", "", "aaaaaa"),
        ("([^ab]*)*", "", "ccccxx"),
        ("([^ab]*)*", "", "ababab"),
        ("((z)+|a)*", "", "zabcde"),
        ("a+?", "", "aaaaaa"),
        ("(a)", "", "aaa"),
        ("(a*?)", "", "aaa"),
        ("(a)*?", "", "aaa"),
        ("(a*?)*?", "", "aaa"),
        ("(a*)*(x)", "", "x"),
        ("(a*)*(x)", "", "ax"),
        ("(a*)*(x)", "", "axa"),
        ("(a*)*(x)(\\1)", "", "x"),
        ("(a*)*(x)(\\1)", "", "ax"),
        ("(a*)*(x)(\\1)", "", "axa"),
        ("(a*)*(x)(\\1)(x)", "", "axax"),
        ("(a*)*(x)(\\1)(x)", "", "axxa"),
        ("(a*)*(x)", "", "x"),
        ("(a*)*(x)", "", "ax"),
        ("(a*)*(x)", "", "axa"),
        ("(a*)+(x)", "", "x"),
        ("(a*)+(x)", "", "ax"),
        ("(a*)+(x)", "", "axa"),
        ("(a*){2}(x)", "", "x"),
        ("(a*){2}(x)", "", "ax"),
        ("(a*){2}(x)", "", "axa"),
        ("((..)|(.))", "", "a"),
        ("((..)|(.))((..)|(.))", "", "a"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "a"),
        ("((..)|(.)){1}", "", "a"),
        ("((..)|(.)){2}", "", "a"),
        ("((..)|(.)){3}", "", "a"),
        ("((..)|(.))*", "", "a"),
        ("((..)|(.))", "", "aa"),
        ("((..)|(.))((..)|(.))", "", "aa"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "aa"),
        ("((..)|(.)){1}", "", "aa"),
        ("((..)|(.)){2}", "", "aa"),
        ("((..)|(.)){3}", "", "aa"),
        ("((..)|(.))*", "", "aa"),
        ("((..)|(.))", "", "aaa"),
        ("((..)|(.))((..)|(.))", "", "aaa"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "aaa"),
        ("((..)|(.)){1}", "", "aaa"),
        ("((..)|(.)){2}", "", "aaa"),
        ("((..)|(.)){3}", "", "aaa"),
        ("((..)|(.))*", "", "aaa"),
        ("((..)|(.))", "", "aaaa"),
        ("((..)|(.))((..)|(.))", "", "aaaa"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "aaaa"),
        ("((..)|(.)){1}", "", "aaaa"),
        ("((..)|(.)){2}", "", "aaaa"),
        ("((..)|(.)){3}", "", "aaaa"),
        ("((..)|(.))*", "", "aaaa"),
        ("((..)|(.))", "", "aaaaa"),
        ("((..)|(.))((..)|(.))", "", "aaaaa"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "aaaaa"),
        ("((..)|(.)){1}", "", "aaaaa"),
        ("((..)|(.)){2}", "", "aaaaa"),
        ("((..)|(.)){3}", "", "aaaaa"),
        ("((..)|(.))*", "", "aaaaa"),
        ("((..)|(.))", "", "aaaaaa"),
        ("((..)|(.))((..)|(.))", "", "aaaaaa"),
        ("((..)|(.))((..)|(.))((..)|(.))", "", "aaaaaa"),
        ("((..)|(.)){1}", "", "aaaaaa"),
        ("((..)|(.)){2}", "", "aaaaaa"),
        ("((..)|(.)){3}", "", "aaaaaa"),
        ("((..)|(.))*", "", "aaaaaa"),
        ("X(.?){0,}Y", "", "X1234567Y"),
        ("X(.?){1,}Y", "", "X1234567Y"),
        ("X(.?){2,}Y", "", "X1234567Y"),
        ("X(.?){3,}Y", "", "X1234567Y"),
        ("X(.?){4,}Y", "", "X1234567Y"),
        ("X(.?){5,}Y", "", "X1234567Y"),
        ("X(.?){6,}Y", "", "X1234567Y"),
        ("X(.?){7,}Y", "", "X1234567Y"),
        ("X(.?){8,}Y", "", "X1234567Y"),
        ("X(.?){0,8}Y", "", "X1234567Y"),
        ("X(.?){1,8}Y", "", "X1234567Y"),
        ("X(.?){2,8}Y", "", "X1234567Y"),
        ("X(.?){3,8}Y", "", "X1234567Y"),
        ("X(.?){4,8}Y", "", "X1234567Y"),
        ("X(.?){5,8}Y", "", "X1234567Y"),
        ("X(.?){6,8}Y", "", "X1234567Y"),
        ("X(.?){7,8}Y", "", "X1234567Y"),
        ("X(.?){8,8}Y", "", "X1234567Y"),
        ("(a|ab|c|bcd){0,}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){1,}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){2,}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){3,}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){4,}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){0,10}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){1,10}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){2,10}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){3,10}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd){4,10}(d*)", "", "ababcd"),
        ("(a|ab|c|bcd)*(d*)", "", "ababcd"),
        ("(a|ab|c|bcd)+(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){0,}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){1,}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){2,}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){3,}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){4,}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){0,10}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){1,10}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){2,10}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){3,10}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd){4,10}(d*)", "", "ababcd"),
        ("(ab|a|c|bcd)*(d*)", "", "ababcd"),
        ("(ab|a|c|bcd)+(d*)", "", "ababcd"),
        ("(a|ab)(c|bcd)(d*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d*)", "", "abcd"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a|ab)(c|bcd)(d|.*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d|.*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d|.*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d|.*)", "", "abcd"),
        ("(a|ab)(c|bcd)(d*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d*)", "", "abcd"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a*)(b|abc)(c*)", "", "abc"),
        ("(a*)(abc|b)(c*)", "", "abc"),
        ("(a|ab)(c|bcd)(d|.*)", "", "abcd"),
        ("(a|ab)(bcd|c)(d|.*)", "", "abcd"),
        ("(ab|a)(c|bcd)(d|.*)", "", "abcd"),
        ("(ab|a)(bcd|c)(d|.*)", "", "abcd"),
        ("\u{fb00}", "i", "FF"),
        ("(\u{fb00})\\1", "i", "FFFF"),
        ("(\u{fb00})\\1", "i", "FF\u{fb00}"),
        ("(\u{fb00})\\1", "i", "\u{fb00}FF"),
        ("\u{fb01}", "i", "FI"),
        ("(\u{fb01})\\1", "i", "FIFI"),
        ("\u{fb02}", "i", "FL"),
        ("\u{fb03}", "i", "FFI"),
        ("\u{fb04}", "i", "FFL"),
        ("\u{fb00}I", "i", "\u{fb03}"),
        ("\u{fb03}", "i", "\u{fb00}I"),
        ("F\u{fb01}", "i", "\u{fb03}"),
        ("\u{fb03}", "i", "F\u{fb01}"),
        ("\u{fb00}L", "i", "\u{fb04}"),
        ("\u{fb04}", "i", "\u{fb00}L"),
        ("F\u{fb02}", "i", "\u{fb04}"),
        ("\u{fb04}", "i", "F\u{fb02}"),
        ("[\u{fb04}[=a=]o]+", "i", "F\u{fb02}aÄö"),
        ("\u{1f50}", "i", "\u{03c5}\u{0313}"),
        ("\u{1f52}", "i", "\u{03c5}\u{0313}\u{0300}"),
        ("\u{1f54}", "i", "\u{03c5}\u{0313}\u{0301}"),
        ("\u{1f56}", "i", "\u{03c5}\u{0313}\u{0342}"),
        ("\u{1f50}\u{0300}", "i", "\u{1f52}"),
        ("\u{1f52}", "i", "\u{1f50}\u{0300}"),
        ("\u{1f50}\u{0301}", "i", "\u{1f54}"),
        ("\u{1f54}", "i", "\u{1f50}\u{0301}"),
        ("\u{1f50}\u{0342}", "i", "\u{1f56}"),
        ("\u{1f56}", "i", "\u{1f50}\u{0342}"),
        ("\u{1fb6}", "i", "\u{03b1}\u{0342}"),
        ("\u{1fb7}", "i", "\u{03b1}\u{0342}\u{03b9}"),
        ("\u{1fb6}\u{03b9}", "i", "\u{1fb7}"),
        ("\u{1fb7}", "i", "\u{1fb6}\u{03b9}"),
        ("\u{1fc6}", "i", "\u{03b7}\u{0342}"),
        ("\u{1fc7}", "i", "\u{03b7}\u{0342}\u{03b9}"),
        ("\u{1fc6}\u{03b9}", "i", "\u{1fc7}"),
        ("\u{1fc7}", "i", "\u{1fc6}\u{03b9}"),
        ("\u{1ff6}", "i", "\u{03c9}\u{0342}"),
        ("\u{1ff7}", "i", "\u{03c9}\u{0342}\u{03b9}"),
        ("\u{1ff6}\u{03b9}", "i", "\u{1ff7}"),
        ("\u{1ff7}", "i", "\u{1ff6}\u{03b9}"),
        ("f*", "i", "ff"),
        ("f*", "i", "\u{fb00}"),
        ("f+", "i", "ff"),
        ("f+", "i", "\u{fb00}"),
        ("f{1,}", "i", "ff"),
        ("f{1,}", "i", "\u{fb00}"),
        ("f{1,2}", "i", "ff"),
        ("f{1,2}", "i", "\u{fb00}"),
        ("f{,2}", "i", "ff"),
        ("f{,2}", "i", "\u{fb00}"),
        ("ff?", "i", "ff"),
        ("ff?", "i", "\u{fb00}"),
        ("f{2}", "i", "ff"),
        ("f{2}", "i", "\u{fb00}"),
        ("f{2,2}", "i", "ff"),
        ("f{2,2}", "i", "\u{fb00}"),
        ("K", "i", "\u{212a}"),
        ("k", "i", "\u{212a}"),
        ("\\w", "i", "\u{212a}"),
        ("\\W", "i", "\u{212a}"),
        ("[\\w]", "i", "\u{212a}"),
        ("[\\w]+", "i", "a\\wWc"),
        ("[\\W]+", "i", "a\\wWc"),
        ("[\\d]+", "i", "0\\dD9"),
        ("[\\D]+", "i", "a\\dDc"),
        ("[\\s]+", "i", " \\sS\t"),
        ("[\\S]+", "i", " \\sS\t"),
        ("[kx]", "i", "\u{212a}"),
        ("ff", "i", "\u{fb00}"),
        ("[f]f", "i", "\u{fb00}"),
        ("f[f]", "i", "\u{fb00}"),
        ("[f][f]", "i", "\u{fb00}"),
        ("(?:f)f", "i", "\u{fb00}"),
        ("f(?:f)", "i", "\u{fb00}"),
        ("(?:f)(?:f)", "i", "\u{fb00}"),
        ("\\A[\u{fb00}]\\z", "i", "\u{fb00}"),
        ("\\A[\u{fb00}]\\z", "i", "ff"),
        ("\\A[^\u{fb00}]\\z", "i", "\u{fb00}"),
        ("\\A[^\u{fb00}]\\z", "i", "ff"),
        ("\\A[^[^\u{fb00}]]\\z", "i", "\u{fb00}"),
        ("\\A[^[^\u{fb00}]]\\z", "i", "ff"),
        ("\\A[[^[^\u{fb00}]]]\\z", "i", "\u{fb00}"),
        ("\\A[[^[^\u{fb00}]]]\\z", "i", "ff"),
        ("[^a-c]", "i", "A"),
        ("[[^a-c]]", "i", "A"),
        ("[^a]", "i", "a"),
        ("[[^a]]", "i", "a"),
        ("\\A\\W\\z", "i", "\u{fb00}"),
        ("\\A\\W\\z", "i", "ff"),
        ("\\A[\\p{L}]\\z", "i", "\u{fb00}"),
        ("\\A[\\p{L}]\\z", "i", "ff"),
        ("\\A\\W\\z", "i", "\u{fb03}"),
        ("\\A\\W\\z", "i", "ffi"),
        ("\\A\\W\\z", "i", "\u{fb00}i"),
        ("\\A[\\p{L}]\\z", "i", "\u{fb03}"),
        ("\\A[\\p{L}]\\z", "i", "ffi"),
        ("\\A[\\p{L}]\\z", "i", "\u{fb00}i"),
        ("([[=a=]])\\1", "i", "aA"),
        ("([[=a=]])\\1", "i", "Aa"),
        ("([[=a=]])\\1", "i", "a\u{00e4}"),
        ("([[=a=]])\\1", "i", "a\u{00c4}"),
        ("([[=a=]])\\1", "i", "\u{00e4}a"),
        ("([[=a=]])\\1", "i", "\u{00c4}a"),
        ("([[=a=]])\\1", "i", "\u{00c4}A"),
        ("[[=a=]o]+", "i", "\u{00e4}O\u{00f6}"),
        ("[[=a=]o]+", "i", "\u{00e4}O\u{00f6}"),
        ("[[=\u{00df}=]o]+", "i", "s"),
        ("[[=\u{00df}=]o]+", "i", "ss"),
        ("[[=\u{00df}=]o]+", "", "s"),
        ("[[=\u{00df}=]o]+", "", "ss"),
        ("[\u{0132}]+", "", "ij"),
        ("[\u{0132}]+", "i", "ij"),
        ("[[=\u{0132}=]]+", "", "ij"),
        ("[[=\u{0132}=]o]+", "", "ij"),
        ("[[=\u{0132}=]o]+", "i", "ij"),
        ("[\\s-r]+", "", "\\stu"),
        ("[\\s-v]+", "", "\\stu"),
        ("$(\\A|)", "", "x"),
        ("(^\\w)|()^", "", "empty"),
        ("x(y|())", "", "xy"),
        ("(x|())*", "", "xxx"),
        ("a(\\z|())", "", "a"),
        ("a??+", "", "aaa"),
        ("()??()??()??()??()??()??()??()??\\3\\5\\7", "", "a"),
        ("()*", "", "a"),
        ("(a|)*", "", "a"),
        ("(|a)?", "", "a"),
        ("(a|())*", "", "a"),
        ("()??\\1", "", "a"),
        ("(a|())*?\\2", "", "a"),
        ("(a*)+", "", "a"),
        ("(\\1a|){2}", "", "aa"),
        ("(|[ab]){3,3}b", "", "aab"),
        ("(|[ab]){3}b", "", "aab"),
        ("(|a){3}b", "", "aab"),
        ("(|a){2}b", "", "ab"),
        ("(|a){1}b", "", "b"),
        ("(|a)b", "", "b"),
        ("(|a)(|a)(|a)b", "", "aab"),
        ("(|a)(|a)b", "", "ab"),
        ("(|a+?){0,4}b", "", "aaab")
    ] {
        let from_index = 1;
        let e_pattern = java_string_escape(pattern);
        let e_input = java_string_escape(input);
        match run_test(&mut statement, &pattern, &flags, &input, from_index)? {
            TestResult::Match(groups) => {
                writeln!(out, "test(\"{}\", \"{}\", \"{}\", {}, true, {});", e_pattern, flags, e_input, from_index - 1, groups.iter().map(|v| format!("{}", v)).collect::<Vec<String>>().join(", "))?;
            }
            TestResult::NoMatch => {
                writeln!(out, "test(\"{}\", \"{}\", \"{}\", {}, false);", e_pattern, flags, e_input, from_index - 1)?;
            }
            TestResult::SyntaxError(message) => {
                writeln!(out, "expectSyntaxError(\"{}\", \"{}\", \"{}\");", e_pattern, flags, java_string_escape(message.as_str()))?;
            }
        }
    }
    insert_generated_code(Path::new(PATH_GRAAL_REPO).join(PATH_ORACLE_DB_TESTS).as_path(), &out)?;
    Ok(())
}
