package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

import com.oracle.truffle.api.operation.OperationNode.SourceInfo;
import com.oracle.truffle.api.source.Source;

public class BuilderSourceInfo {

    private static final int NOT_AVAILABLE = -1;

    private Stack<Integer> sourceStack = new Stack<>();
    int currentSource = -1;

    private ArrayList<Source> sourceList = new ArrayList<>();

    private static class SourceData {
        final int start;
        int length;
        final int sourceIndex;

        public SourceData(int start, int sourceIndex) {
            this.start = start;
            this.sourceIndex = sourceIndex;
        }
    }

    private ArrayList<Integer> bciList = new ArrayList<>();
    private ArrayList<SourceData> sourceDataList = new ArrayList<>();
    private Stack<SourceData> sourceDataStack = new Stack<>();

    public BuilderSourceInfo() {
    }

    public void reset() {
        sourceStack.clear();
        sourceDataList.clear();
        sourceDataStack.clear();
        bciList.clear();
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

        SourceData data = new SourceData(start, currentSource);

        bciList.add(bci);
        sourceDataList.add(data);
        sourceDataStack.add(data);
    }

    public void endSourceSection(int bci, int length) {
        SourceData data = sourceDataStack.pop();
        data.length = length;

        SourceData prev;
        if (sourceDataStack.isEmpty()) {
            prev = new SourceData(-1, currentSource);
            prev.length = -1;
        } else {
            prev = sourceDataStack.peek();
        }

        bciList.add(bci);
        sourceDataList.add(prev);
    }

    public Source[] buildSource() {
        return sourceList.toArray(new Source[sourceList.size()]);
    }

    public SourceInfo build() {
        if (!sourceStack.isEmpty()) {
            throw new IllegalStateException("not all sources ended");
        }
        if (!sourceDataStack.isEmpty()) {
            throw new IllegalStateException("not all source sections ended");
        }

        int size = bciList.size();

        int[] bciArray = new int[size];
        int[] startArray = new int[size];
        int[] lengthArray = new int[size];
        int[] sourceIndexArray = new int[size];

        int index = 0;
        int lastBci = -1;
        boolean isFirst = true;

        for (int i = 0; i < size; i++) {
            SourceData data = sourceDataList.get(i);
            int curBci = bciList.get(i);

            if (data.start == NOT_AVAILABLE && isFirst) {
                // skip over all leading -1s
                continue;
            }

            isFirst = false;

            if (curBci == lastBci && index > 1) {
                // overwrite if same bci
                index--;
            }

            bciArray[index] = curBci;
            startArray[index] = data.start;
            lengthArray[index] = data.length;
            sourceIndexArray[index] = data.sourceIndex;

            index++;
            lastBci = curBci;
        }

        return new SourceInfo(
                        Arrays.copyOf(bciArray, index),
                        Arrays.copyOf(sourceIndexArray, index),
                        Arrays.copyOf(startArray, index),
                        Arrays.copyOf(lengthArray, index));
    }
}
