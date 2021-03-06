package com.redhat.ceylon.compiler.java.tools;

import javax.tools.JavaFileObject;

import com.redhat.ceylon.compiler.java.codegen.CeylonFileObject;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

public class CeylonLog extends Log {

    /** Get the Log instance for this context. */
    public static Log instance(Context context) {
        Log instance = context.get(logKey);
        if (instance == null)
            instance = new CeylonLog(context);
        return instance;
    }

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(final Context context) {
        context.put(logKey, new Context.Factory<Log>() {
            public Log make() {
                return new CeylonLog(context);
            }
        });
    }

    private boolean majorVersionWarning = false;

    protected CeylonLog(Context context) {
        super(context);
    }

    @Override
    public void report(JCDiagnostic diagnostic) {
        DiagnosticSource source = diagnostic.getDiagnosticSource();
        if(source != null){
            JavaFileObject file = source.getFile();
            if(file instanceof CeylonFileObject && diagnostic.getType() == DiagnosticType.ERROR){
                ((CeylonFileObject)file).errors++;
            }
        }
        super.report(diagnostic);
    }
    
    @Override
    public void note(JavaFileObject file, String key, Object... args) {
        // Ignore lint warnings
    }

    @Override
    public void mandatoryNote(JavaFileObject file, String key, Object... args) {
        // Ignore lint warnings
    }

    @Override
    public void warning(String key, Object... args) {
        // limit the number of warnings for Java 7 classes
        if("big.major.version".equals(key)){
            // change the key to a more helpful message
            key = "ceylon.big.major.version";
            if(!majorVersionWarning )
                majorVersionWarning = true;
            else // we already warned once
                return;
        }
        super.warning(key, args);
    }
}
