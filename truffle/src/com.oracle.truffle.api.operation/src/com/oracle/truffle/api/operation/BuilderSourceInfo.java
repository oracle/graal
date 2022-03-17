package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Stack;

import com.oracle.truffle.api.source.Source;

public class BuilderSourceInfo {

    private static final int NOT_AVAILABLE = -1;

    private Stack<Integer> sourceStack = new Stack<>();
    int currentSource = -1;

    private ArrayList<Source> sourceList = new ArrayList<>();

    private static class SourceData {
        final int bci;
        final int start;
        int length;
        final int sourceIndex;

        public SourceData(int bci, int start, int sourceIndex) {
            this.bci = bci;
            this.start = start;
            this.sourceIndex = sourceIndex;
        }
    }

    private ArrayList<SourceData> sourceDataList = new ArrayList<>();
    private Stack<SourceData> sourceDataStack = new Stack<>();

    public BuilderSourceInfo() {
    }

    public void reset() {
        sourceStack.clear();
        sourceDataList.clear();
        sourceDataStack.clear();
    }

    public void beginSource(int bci, Source src) {
        int idx = -1;
        for (int i = 0; i < sourceList.size(); i++) {
            if (sourceList.get(i) == src) {
                idx = i;
                break;
            }
        }

        if (idx == -1) {
            idx = sourceList.size();
            sourceList.add(src);
        }

        sourceStack.push(currentSource);

        currentSource = idx;
        beginSourceSection(bci, NOT_AVAILABLE);
    }

    public void endSource(int bci) {
        endSourceSection(bci, NOT_AVAILABLE);
        currentSource = sourceStack.pop();
    }

    public void beginSourceSection(int bci, int start) {

        SourceData data = new SourceData(bci, start, currentSource);

        sourceDataList.add(data);
        sourceDataStack.add(data);
    }

    public void endSourceSection(int bci, int length) {
        SourceData data = sourceDataStack.pop();
        data.length = length;

        SourceData prev;
        if (sourceDataStack.isEmpty()) {
            prev = new SourceData(bci, -1, currentSource);
            prev.length = -1;
        } else {
            prev = sourceDataStack.peek();
        }

        sourceDataList.add(prev);
    }

    private static int[] copyList(ArrayList<Integer> list) {
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    public Source[] buildSource() {
        return sourceList.toArray(new Source[sourceList.size()]);
    }

    public int[][] build() {
        if (!sourceStack.isEmpty()) {
            throw new IllegalStateException("not all sources ended");
        }
        if (!sourceDataStack.isEmpty()) {
            throw new IllegalStateException("not all source sections ended");
        }

        int[] bciArray = new int[sourceDataList.size()];
        int[] startArray = new int[sourceDataList.size()];
        int[] lengthArray = new int[sourceDataList.size()];
        int[] sourceIndexArray = new int[sourceDataList.size()];

        int index = 0;
        int lastBci = -1;
        boolean isFirst = true;

        for (SourceData data : sourceDataList) {
            if (data.start == NOT_AVAILABLE && isFirst) {
                // skip over all leading -1s
                continue;
            }

            isFirst = false;

            if (data.bci == lastBci && index > 1) {
                // overwrite if same bci
                index--;
            }

            bciArray[index] = data.bci;
            startArray[index] = data.start;
            lengthArray[index] = data.length;
            sourceIndexArray[index] = data.sourceIndex;

            index++;
            lastBci = data.bci;
        }

        return new int[][]{
                        Arrays.copyOf(bciArray, index),
                        Arrays.copyOf(startArray, index),
                        Arrays.copyOf(lengthArray, index),
                        Arrays.copyOf(sourceIndexArray, index),
        };
    }
}
