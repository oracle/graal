package com.oracle.truffle.example.simplegraphanalitycs;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.util.function.Supplier;

public class TruffleExt {
    
    public static RootCallTarget createCallTarget(Supplier r) {
        RootCallTarget rootTarget = Truffle.getRuntime().createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
            @Override
            public Object execute(VirtualFrame vf) {
                return r.get();
            }
        });
        return rootTarget;
    }
    
    public class TestLanguage extends TruffleLanguage {

        @Override
        protected Object createContext(TruffleLanguage.Env env) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected CallTarget parse(Source source, Node node, String... strings) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Object findExportedSymbol(Object c, String string, boolean bln) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Object getLanguageGlobal(Object c) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected boolean isObjectOfLanguage(Object o) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Visualizer getVisualizer() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected boolean isInstrumentable(Node node) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected WrapperNode createWrapperNode(Node node) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Object evalInContext(Source source, Node node, MaterializedFrame mf) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
}
