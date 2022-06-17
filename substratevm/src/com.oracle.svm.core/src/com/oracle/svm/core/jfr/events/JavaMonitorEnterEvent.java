package src.com.oracle.svm.core.src.com.oracle.svm.core.jfr.events;


import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;


public class JavaMonitorEnterEvent {

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(Object obj, org.graalvm.nativeimage.IsolateThread previousOwner,long startTicks) {
        emit(obj, com.oracle.svm.core.jfr.SubstrateJVM.get().getThreadId(previousOwner), startTicks);
    }
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(Object obj, long previousOwner, long startTicks) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.JavaMonitorEnter)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            if (emit0(data, obj, previousOwner, startTicks, SubstrateJVM.get().isLarge(JfrEvent.JavaMonitorEnter)) == com.oracle.svm.core.jfr.JfrEventWriteStatus.RetryLarge) {
                SubstrateJVM.get().setLarge(JfrEvent.JavaMonitorEnter, true);
                emit0(data, obj, previousOwner, startTicks, true);
            }
        }
    }
        @Uninterruptible(reason = "Accesses a JFR buffer.")
        private static com.oracle.svm.core.jfr.JfrEventWriteStatus emit0(JfrNativeEventWriterData data, Object obj, long previousOwner, long startTicks, boolean isLarge){
            JfrNativeEventWriter.beginEvent(data, JfrEvent.JavaMonitorEnter, isLarge);

            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks() - startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ThreadStart.getId(), 0));
            JfrNativeEventWriter.putClass(data, obj.getClass());
            JfrNativeEventWriter.putLong(data, previousOwner);
            JfrNativeEventWriter.putLong(data, org.graalvm.compiler.word.Word.objectToUntrackedPointer(obj).rawValue());

            return  JfrNativeEventWriter.endEvent(data, isLarge);
        }
}
