package ir.pedalpro.app.nativeapp;

/** Presentation-only null guard: never mutates server payloads or numeric zero. */
public final class UiSafeText {
    private UiSafeText(){}
    public static String display(String value){
        if(value==null||value.trim().isEmpty()||"null".equalsIgnoreCase(value.trim()))
            return "—";
        return value.replaceAll("(?i)\\bnull\\b","—");
    }
}
