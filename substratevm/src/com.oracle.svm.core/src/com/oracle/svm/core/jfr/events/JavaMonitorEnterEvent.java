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
    public static void emit(Object obj, org.graalvm.nativeimage.IsolateThread previousOwner, long addr) {
        emit(obj, com.oracle.svm.core.jfr.SubstrateJVM.get().getThreadId(previousOwner), addr);
    }
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(Object obj, long previousOwner, long addr) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.JavaMonitorEnter)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            if (emit0(data, obj, previousOwner, addr, SubstrateJVM.get().isLarge(JfrEvent.JavaMonitorEnter)) == com.oracle.svm.core.jfr.JfrEventWriteStatus.RetryLarge) {
                SubstrateJVM.get().setLarge(JfrEvent.JavaMonitorEnter, true);
                emit0(data, obj, previousOwner, addr, true);
            }
        }
    }
        @Uninterruptible(reason = "Accesses a JFR buffer.")
        private static com.oracle.svm.core.jfr.JfrEventWriteStatus emit0(JfrNativeEventWriterData data, Object obj, long previousOwner, long addr, boolean isLarge){
            JfrNativeEventWriter.beginEvent(data, JfrEvent.JavaMonitorEnter, isLarge);

            JfrNativeEventWriter.putClass(data, obj.getClass());

            //for some reason we need both of these ()which do the same thing
            JfrNativeEventWriter.putEventThread(data);// same as put thread, but uses the current thread
            JfrNativeEventWriter.putLong(data, previousOwner);//basically just does putlong(threadID, based on thread pointer)

            JfrNativeEventWriter.putLong(data, addr);//this should show up as 0 but it seems random

            return  JfrNativeEventWriter.endEvent(data, isLarge);
        }
}
