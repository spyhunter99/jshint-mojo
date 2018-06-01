package com.cj.jshintmojo.jshint;

import java.io.File;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharEncoding;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import java.io.IOException;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.cj.jshintmojo.util.Rhino;
import org.apache.commons.io.FileUtils;

public class JSHint {

    private final Rhino rhino;

    public JSHint(File customJSHint) throws IOException {
        rhino = createRhino(FileUtils.readFileToString(customJSHint, "UTF-8"));
    }

    public JSHint(String jshintCode) {
        rhino = createRhino(resourceAsString(jshintCode));
    }

    private static Rhino createRhino(final String code) {
        Rhino result = new Rhino();
        try {
            result.eval(
                    "print=function(){};" +
                    "quit=function(){};" +
                    "arguments=[];");

            result.eval(commentOutTheShebang(code));
        } catch (EcmaError e) {
            throw new RuntimeException("Javascript eval error:" + e.getScriptStackTrace(), e);
        }
        return result;
    }

    private static String commentOutTheShebang(String code) {
        String minusShebang = code.startsWith ("#!") ? "//" + code : code;
        return minusShebang;
    }

    public List<Hint> run(final InputStream source, final String options, final String globals) {
        final List<Hint> results = new ArrayList<JSHint.Hint>();

        String sourceAsText = toString (source);

        NativeObject nativeOptions = toJsObject (options);
        NativeObject nativeGlobals = toJsObject (globals);

        Boolean codePassesMuster = rhino.call ("JSHINT", sourceAsText, nativeOptions, nativeGlobals);

        if (!codePassesMuster) {
            NativeArray errors = rhino.eval ("JSHINT.errors");

            for (Object next : errors) {
                if(next != null){ // sometimes it seems that the last error in the list is null
                    JSObject jso = new JSObject(next);
                    if (jso.dot("id").toString().equals("(error)")) {
                        results.add(Hint.createHint(jso));
                }
            }
        }
        }

        return results;
    }

    private NativeObject toJsObject(final String options) {
        NativeObject nativeOptions = new NativeObject ();
        for (final String nextOption : options.split (",")) {
            final String option = nextOption.trim ();
            if (!option.isEmpty ()) {
                final String name;
                final Object value;

                final int valueDelimiter = option.indexOf (':');
                if (valueDelimiter == -1) {
                    name = option;
                    value = Boolean.TRUE;
                } else {
                    name = option.substring (0, valueDelimiter);
                    String rest = option.substring (valueDelimiter + 1).trim ();
                    if (rest.matches ("[0-9]+")) {
                        value = Integer.parseInt (rest);
                    } else if (rest.equals ("true")) {
                        value = Boolean.TRUE;
                    } else if (rest.equals ("false")) {
                        value = Boolean.FALSE;
                    } else {
                        value = rest;
                    }
                }
                nativeOptions.defineProperty(name, value, ScriptableObject.READONLY);
            }
        }
        return nativeOptions;
    }

    private static String toString(final InputStream in) {
        try {
            return IOUtils.toString (in, CharEncoding.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException (e);
        }
    }

    private String resourceAsString(final String name) {
        return toString (getClass ().getResourceAsStream (name));
    }

    @SuppressWarnings("unchecked")
    static class JSObject {
        private final NativeObject a;

        public JSObject(final Object o) {
            if(o==null) {
                throw new NullPointerException();
            }
            this.a = (NativeObject) o;
        }

        public <T> T dot(final String name){
            return (T) a.get (name);
        }
    }

    public static abstract class Hint {
        public String id, code, raw, evidence, reason;
        public Number line, character;

        public Hint(final JSObject o) {
            id = nullSafeToString (o, "id");
            code = nullSafeToString (o, "code");
            raw = nullSafeToString (o, "raw");
            evidence = nullSafeToString (o, "evidence");
            line = o.dot ("line");
            character = o.dot ("character");
            reason = nullSafeToString (o, "reason");
        }

        public Hint() { }
        
        public String printLogMessage() {
            String line = (this.line != null) ? String.valueOf(this.line.intValue()) : "";
            String character = (this.character != null) ? String.valueOf(this.character.intValue()) : "";
            return "   " + line + "," + character + ": " + this.reason + " \t(" + this.code + ")";
        }
        
        public static Hint createHint(final JSObject jso) {
            if (jso == null)
            	throw new IllegalArgumentException();
            String code = (String)jso.dot("code");
            
            char c = code.charAt(0);
            Hint hint;
            switch (c) {
                case 'E':
                    hint = new Error(jso);
                    break;
                case 'W':
                    hint = new Warning(jso);
                    break;
                case 'I':
                    hint = new Info(jso);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected char c=" + c);
            }
            return hint;
        }

    }

    private static String nullSafeToString (JSObject o, String name) {
            return o.dot (name) != null ? o.dot (name).toString () : "";
        }
    
    @SuppressWarnings("serial")
    public static class Warning extends Hint implements Serializable {

        public Warning(final JSObject o) {
            super(o);
        }

        

        // NOTE: for Unit Testing purpose.
        public Warning() { }
        }

    @SuppressWarnings("serial")
    public static class Error extends Hint implements Serializable {

        public Error(final JSObject o) {
            super(o);
    }

        // NOTE: for Unit Testing purpose.
        public Error() { }
}
    
    @SuppressWarnings("serial")
    public static class Info extends Hint implements Serializable {

        public Info(final JSObject o) {
            super(o);
        }

        // NOTE: for Unit Testing purpose.
        public Info() { }
    }
}
