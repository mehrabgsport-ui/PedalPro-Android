package ir.pedalpro.diagnostic;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticReport {
    private final List<String> items = new ArrayList<>();

    public void add(String item) {
        items.add(item);
    }

    public List<String> getItems() {
        return items;
    }
}
