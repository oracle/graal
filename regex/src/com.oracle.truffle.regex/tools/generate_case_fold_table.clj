#!/usr/bin/env boot

; ------------------------------------------------------------------------------
; Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;
; The Universal Permissive License (UPL), Version 1.0
;
; Subject to the condition set forth below, permission is hereby granted to any
; person obtaining a copy of this software, associated documentation and/or
; data (collectively the "Software"), free of charge and under any and all
; copyright rights in the Software, and any and all patent rights owned or
; freely licensable by each licensor hereunder covering either (i) the
; unmodified Software as contributed to or provided by such licensor, or (ii)
; the Larger Works (as defined below), to deal in both
;
; (a) the Software, and
;
; (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
; one is included with the Software each a "Larger Work" to which the Software
; is contributed by such licensors),
;
; without restriction, including without limitation the rights to copy, create
; derivative works of, display, perform, and distribute the Software and make,
; use, sell, offer for sale, import, export, have made, and have sold the
; Software and the Larger Work(s), and to sublicense the foregoing rights on
; either these or other terms.
;
; This license is subject to the following condition:
;
; The above copyright notice and either this complete permission notice or at a
; minimum a reference to the UPL must be included in all copies or substantial
; portions of the Software.
;
; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
; SOFTWARE.
; ------------------------------------------------------------------------------

;; In order to run this script, install Boot as described in
;; https://github.com/boot-clj/boot#install or simply evaluate the code below in
;; any Clojure REPL and then call the `-main` function.

;; This script assumes that the current working directory contains a folder "dat"
;; with the files NonUnicodeFoldTable.txt and UnicodeFoldTable.txt.

(require '[clojure.set :as set]
         '[clojure.string :as str])

(defn pairwise
  "Given a sequence `x_1`, `x_2`, `x_3`..., returns the sequence of pairs `[x_1 x_2]`, `[x_2 x_3]`..."
  [xs]
  (map vector xs (rest xs)))

(defn parse-relation-file
  "Parses a binary relation from the file at `path` and returns it as a sorted
  set."
  [path]
  (into (sorted-set)
        (apply concat
          (for [line (str/split-lines (slurp path))]
            (let [codepoints-str (str/split line #";")
                  parse-hex      #(Long/parseLong % 16)
                  codepoints     (map parse-hex codepoints-str)]
              (pairwise codepoints))))))

(defn maps-to
  "Given a binary relation `rel`, represented as a sorted set, finds the set of
  elements Y such that X `rel` Y."
  [rel elem]
  (map second (subseq rel > [elem 0] < [(inc elem) 0])))

(defn swap
  "Swaps the elements in a pair."
  [[a b]]
  [b a])

(defn remove-reflexive-entries
  "Remove pairs of the form [x x] from a given set of pairs."
  [rel]
  (set/select #(not= (first %) (second %)) rel))

(defn symmetric-closure
  "Calculates the symmetric closure of the binary relation `rel`."
  [rel]
  (let [symmetric-rel (into (sorted-set) (map swap rel))]
    (set/union rel symmetric-rel)))

(defn load-relation
  "Loads an equivalence relation from a file and makes it symmetric.

  We do not want a reflexive closure because we want the entries in the relation
  to correspond to equivalent pairs that still need to be encoded in the case
  fold table (and we do not want to include reflexive entries in the case fold
  table). We do not need transitivity because we handle all equivalence classes
  of size larger than 3 in the first step, `extract-large-classes`, and in that
  step we traverse the relation graph recursively."
  [path]
  (-> path
      parse-relation-file
      remove-reflexive-entries
      symmetric-closure))

(def python-ascii-relation
  "The case-folding equivalence relation for Python ascii regular expressions."
  (->> (map vector (range (int \a) (inc (int \z)))
                   (range (int \A) (inc (int \Z))))
       (into (sorted-set))
       symmetric-closure))

(defn collect-eq-classes
  "Given some equivalence relation `rel`, finds the equivalence classes.

  NB: This function assumes that `rel` is only symmetric. Transitive pairs need
  not be included since the graph is being searched."
  [rel]
  (let [find-class (fn [rel start-elem]
                     (let [visited? (atom (sorted-set))]
                       (letfn [(traverse [elem]
                                 (when-not (@visited? elem)
                                   (swap! visited? conj elem)
                                   (doseq [eq-elem (maps-to rel elem)]
                                     (traverse eq-elem))))]
                         (do (traverse start-elem)
                             @visited?))))]
    (loop [rel     rel
           from    [0 0]
           classes []]
      (if-let [next-pair (first (subseq rel > from))]
        (let [class (find-class rel (first next-pair))]
          (recur (set/select #(not-any? class %) rel)
                 next-pair
                 (conj classes class)))
        classes))))

(defn encode-classes
  "Given a list of equivalence classes, generates case fold table entries that
  encode them. For classes of size 2, we encode them as two entries,
  deltaPositive and deltaNegative (:kind :delta). For classes of larger size, we
  use directMapping (:kind :class)."
  [classes]
  (let [;; `class-as-ranges` represents a `class` as a union of closed
        ;; intervals (ranges). This representation is then used inside the
        ;; CHARACTER_SET_TABLE generated by `show-classes`.`
        class-as-ranges (fn [class]
                          (loop [class     class
                                 cur-range nil
                                 ranges    []]
                            (if-let [elem (first class)]
                              (if cur-range
                                (if (= (inc (:hi cur-range)) elem)
                                  (recur (rest class) (update cur-range :hi inc) ranges)
                                  (recur (rest class) {:lo elem, :hi elem} (conj ranges cur-range)))
                                (recur (rest class) {:lo elem, :hi elem} ranges))
                              (if cur-range
                                (conj ranges cur-range)
                                ranges))))
        encode-class    (fn [class]
                          (cond
                            (<= (count class) 1)
                            []
                            (= (count class) 2)
                            (let [lower  (first class)
                                  higher (second class)]
                              [{:lo    lower
                                :hi    lower
                                :delta (- higher lower)
                                :kind  :delta}
                               {:lo    higher
                                :hi    higher
                                :delta (- lower higher)
                                :kind  :delta}])
                            :otherwise
                            (let [class-ranges (class-as-ranges class)]
                              (for [range class-ranges]
                                {:lo    (:lo range)
                                 :hi    (:hi range)
                                 :class class-ranges
                                 :kind  :class}))))]
    (mapcat encode-class classes)))

(defn extract-large-classes
  "This is the first step in encoding the equivalence relation `rel` into a list
  of case fold table entries. This step finds any equivalence classes of size >=
  3 and encodes them using directMapping (:kind :class) because the other
  heuristics only deal well with equivalence classes of size 2.

  NB: directMapping is a case fold table entry which assigns to a range of code
  points a specific set of equivalent code points."
  [rel]
  (let [large-classes   (filter #(>= (count %) 3) (collect-eq-classes rel))
        processed-elems (apply set/union large-classes)
        entries         (encode-classes large-classes)]
    {:todo-rel (set/select #(not-any? processed-elems %) rel)
     :entries entries}))

(defn extract-runs
  "This is a helper function for `extract-delta-runs` and
  `extract-alternating-runs`."
  [rel find-run encode-run]
  (loop [todo-rel rel
         from     [0 0]
         entries  []]
    (if-let [next-pair (first (subseq todo-rel > from))]
      (let [run (find-run rel next-pair)]
        (if (> (count run) 1)
          (recur (set/difference todo-rel run)
                 next-pair
                 (conj entries (encode-run run)))
          (recur todo-rel next-pair entries)))
      {:todo-rel todo-rel
       :entries  entries})))

(defn extract-delta-runs
  "This is the second step in encoding the equivalence relation `rel` into a
  list of code table entries. This step finds ranges of characters which are
  case-equivalent, character by character, to other ranges of characters, e.g.
  the ASCII ranges [a-z] and [A-Z]. These are then encoded via the entries
  deltaPositive and deltaNegative (:kind :delta)."
  [rel]
  (letfn [(find-delta-run [rel start-pair]
            (let [next-pair [(inc (first start-pair)) (inc (second start-pair))]]
              (cons start-pair (when (rel next-pair)
                                 (find-delta-run rel next-pair)))))
          (encode-delta-run [run]
                            {:lo    (first (first run))
                             :hi    (first (last run))
                             :delta (- (second (first run)) (first (first run)))
                             :kind  :delta})]
    (extract-runs rel find-delta-run encode-delta-run)))

(defn extract-alternating-runs
  "This is the third step in encoding the equivalence relation `rel` into a list
  of code table entries. This step finds ranges of characters in which
  lower-case and upper-case variants are alternated, e.g., as in the Latin
  Extended-A range from 0x0100 to 0x012f. These are encoded using the entries
  alternatingAL and alternatingUL (:kind :alternating)."
  [rel]
  (letfn [(find-alternating-run [rel start-pair]
            (when (= (inc (first start-pair)) (second start-pair))
              (let [next-pair [(+ 2 (first start-pair)) (+ 2 (second start-pair))]]
                (cons start-pair (cons (swap start-pair) (when (rel next-pair)
                                                           (find-alternating-run rel next-pair)))))))
          (encode-alternating-run [run]
                                  {:lo      (first (first run))
                                   :hi      (first (last run))
                                   :aligned (even? (first (first run)))
                                   :kind    :alternating})]
    (extract-runs rel find-alternating-run encode-alternating-run)))

(defn generate-entries
  "Given an equivalence relation, calculates its encoding in terms of case fold
  table entries."
  [rel]
  (let [{rel :todo-rel, large-class-entries :entries} (extract-large-classes rel)
        {rel :todo-rel, delta-entries :entries}       (extract-delta-runs rel)
        {rel :todo-rel, alternating-entries :entries} (extract-alternating-runs rel)
        remaining-classes                             (collect-eq-classes rel)
        remaining-class-entries                       (encode-classes remaining-classes)]
    (sort-by (fn [e] [(:lo e) (:hi e)]) (concat large-class-entries delta-entries alternating-entries remaining-class-entries))))

(defn identify-classes
  "Replaces the references to equivalence classes (:class field) in
  directMapping case fold table entries (:kind :class) with numeric
  identifiers (:class-id field). The numeric identifiers are being allocated
  starting from the value of `num-classes-ref` and the mapping from classes to
  identifiers is being stored in `class-ids-ref`."
  [entries num-classes-ref class-ids-ref]
  (doall (for [entry entries]
           (if (= :class (:kind entry))
             (let [class (:class entry)]
               (if-let [class-id (@class-ids-ref class)]
                 (assoc entry :class-id class-id)
                 (let [class-id @num-classes-ref]
                   (do (swap! class-ids-ref assoc class class-id)
                       (swap! num-classes-ref inc)
                       (assoc entry :class-id class-id)))))
             entry))))

(defn show-hex
  "Prints a number in hexadecimal format. Hexadecimal is the conventional base
  in which to write down values of Unicode code points. Also, it is the same
  base as was used in the original case fold table, meaning we can keep the diff
  after updating the table minimal."
  [n]
  (format "0x%04x" n))

(defn show-hex6
  "Prints a number in hexadecimal format. Hexadecimal is the conventional base
  in which to write down values of Unicode code points. Also, it is the same
  base as was used in the original case fold table, meaning we can keep the diff
  after updating the table minimal."
  [n]
  (format "0x%06x" n))

(defn show-classes
  "Renders the CHARACTER_SET_TABLE in Java code. The CHARACTER_SET_TABLE
  contains the definitions of codepoint equivalence classes that are used in
  directMapping (:kind :class) entries of the case fold table."
  [classes]
  (let [header      "    private static final CodePointSet[] CHARACTER_SET_TABLE = new CodePointSet[]{\n"
        item-prefix "                    "
        item-sep    ",\n"
        footer      "};\n"
        show-class  (fn [class]
                      (let [range-sep        ", "
                            show-range       (fn [range]
                                                 (str (show-hex6 (:lo range)) ", " (show-hex6 (:hi range)) ))]
                        (str "rangeSet(" (apply str (interpose ", " (map show-range class))) ")")))
        body        (apply str (interpose item-sep (map #(str item-prefix (show-class %)) classes)))]
    (str header body footer)))

(defn show-entries
  "Renders a case fold table with name `table-name`. This is the main product of
  this script."
  [entries table-name]
  (let [header               (str "    private static final CaseFoldTableImpl " table-name " = new CaseFoldTableImpl(new int[]{\n")
        item-prefix          "                    "
        item-sep             ",\n"
        footer               "\n    });\n"
        method-name-and-args (fn [entry]
                               (case (:kind entry)
                                 :delta       {:lo          (:lo entry) 
                                               :hi          (:hi entry) 
                                               :method-name "INTEGER_OFFSET"
                                               :arg         (:delta entry)}
                                 :alternating {:lo          (:lo entry) 
                                               :hi          (:hi entry)
                                               :method-name (if (:aligned entry)
                                                              "ALTERNATING_AL"
                                                              "ALTERNATING_UL")
                                               :arg         0 }
                                 :class       {:lo          (:lo entry) 
                                               :hi          (:hi entry)
                                               :method-name "DIRECT_MAPPING"
                                               :arg         (:class-id entry)}))
        show-entry           (fn [entry]
                               (let [{:keys [lo hi method-name arg]} (method-name-and-args entry)
                                     arg-sep                    ", "]
                                 (str (show-hex6 lo) ", " (show-hex6 hi) ", " method-name ", " arg)))
        body                 (apply str (interpose item-sep (map #(str item-prefix (show-entry %)) entries)))]
    (str header body footer)))

(defn do-the-job
  "The main function of the script. It loads the definitions of the
  equivalence relations for the cases when the RegExp flag 'u' is not set (file
  NonUnicodeFoldTable.txt) and when it is set (file UnicodeFoldTable.txt). It
  then generates the case fold table entries to be used in the CaseFoldTable
  Java class in TRegex.

  NB: The CHARACTER_SET_TABLE is shared among the two case fold tables because
  there is significant overlap between the two."
  []
  (let [non-unicode-relation    (load-relation "dat/NonUnicodeFoldTable.txt")
        unicode-relation        (load-relation "dat/UnicodeFoldTable.txt")
        python-unicode-relation (load-relation "dat/PythonFoldTable.txt")
        num-classes             (atom 0)
        class-ids               (atom {})
        non-unicode-entries     (identify-classes (generate-entries non-unicode-relation) num-classes class-ids)
        unicode-entries         (identify-classes (generate-entries unicode-relation) num-classes class-ids)
        python-ascii-entries    (identify-classes (generate-entries python-ascii-relation) num-classes class-ids)
        python-unicode-entries  (identify-classes (generate-entries python-unicode-relation) num-classes class-ids)
        classes                 (map second (sort (map swap @class-ids)))]
    (str (show-classes classes)
         "\n"
         (show-entries non-unicode-entries "NON_UNICODE_TABLE_ENTRIES")
         "\n"
         (show-entries unicode-entries "UNICODE_TABLE_ENTRIES")
         "\n"
         (show-entries python-ascii-entries "PYTHON_ASCII_TABLE_ENTRIES")
         "\n"
         (show-entries python-unicode-entries "PYTHON_UNICODE_TABLE_ENTRIES"))))

(defn -main
  "This gets evaluated when we run the script."
  [& args]
  (print (do-the-job)))
