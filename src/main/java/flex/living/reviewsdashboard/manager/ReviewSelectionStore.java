package flex.living.reviewsdashboard.manager;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory selection store; swap with JPA later if needed.
 */
@Component
public class ReviewSelectionStore {
    private final Set<String> selected = ConcurrentHashMap.newKeySet();

    public boolean isSelected(String id) {
        return selected.contains(id);
    }

    public void setSelected(String id, boolean on) {
        if (on) selected.add(id);
        else selected.remove(id);
    }

    public Set<String> all() {
        return Set.copyOf(selected);
    }
}
