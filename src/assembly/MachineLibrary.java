package assembly;

import machinery.StateMachine;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Repository of reusable state machines keyed by id and name. */
public class MachineLibrary implements Serializable {

    private static final long serialVersionUID = -1532645742427132404L;

    private Map<String, StateMachine> machines = new LinkedHashMap<>();
    // Map machine name -> key to enforce unique names and allow lookup by name
    private Map<String, String> nameToKey = new LinkedHashMap<>();

    public String addMachine(StateMachine m) {
        if (m == null) return null;
        String name = m.getName();
        if (name == null) name = "";
        // If a machine with same name already exists, return its key (do not allow duplicates)
        if (nameToKey.containsKey(name)) {
            return nameToKey.get(name);
        }
        String key = UUID.randomUUID().toString();
        machines.put(key, m);
        nameToKey.put(name, key);
        return key;
    }

    public void addMachine(String key, StateMachine m) {
        if (m == null || key == null) return;
        machines.put(key, m);
        String name = m.getName();
        if (name == null) name = "";
        nameToKey.put(name, key);
    }

    public StateMachine get(String key) {
        return machines.get(key);
    }

    public void remove(String key) {
        StateMachine removed = machines.remove(key);
        if (removed != null) {
            String name = removed.getName();
            if (name == null) name = "";
            String existingKey = nameToKey.get(name);
            if (key.equals(existingKey)) {
                nameToKey.remove(name);
            }
        }
    }

    /**
     * Rename the machine identified by key to newName.
     * Returns true if rename succeeded, false if newName is already used or key not found.
     */
    public boolean renameMachine(String key, String newName) {
        if (key == null || newName == null) return false;
        StateMachine m = machines.get(key);
        if (m == null) return false;
        String normalized = newName;
        if (normalized == null) normalized = "";
        // If name already used by another key, fail
        String existing = nameToKey.get(normalized);
        if (existing != null && !existing.equals(key)) return false;

        // remove old mapping
        String oldName = m.getName();
        if (oldName == null) oldName = "";
        nameToKey.remove(oldName);

        // set new name on machine and update map
        m.setName(normalized);
        nameToKey.put(normalized, key);
        return true;
    }

    public Map<String, StateMachine> getMachines() {
        return machines;
    }

    /**
     * Clear all machines and name mappings.
     */
    public void clear() {
        machines.clear();
        nameToKey.clear();
    }

    public String getKeyByName(String name) {
        if (name == null) name = "";
        return nameToKey.get(name);
    }

    public StateMachine getByName(String name) {
        String k = getKeyByName(name);
        return k != null ? get(k) : null;
    }

    public java.util.Set<String> getNames() {
        return nameToKey.keySet();
    }

    /**
     * Synchronize the nameToKey mapping for a machine instance.
     * Call this after external code changes a machine's name directly.
     * Returns the key if found, null otherwise.
     */
    public String syncMachineName(StateMachine machine) {
        if (machine == null) return null;
        // Find the key for this machine instance
        String foundKey = null;
        for (Map.Entry<String, StateMachine> entry : machines.entrySet()) {
            if (entry.getValue() == machine) {
                foundKey = entry.getKey();
                break;
            }
        }
        if (foundKey == null) return null;

        // Remove stale name mappings for this key
        final String keyToRemove = foundKey;
        nameToKey.entrySet().removeIf(e -> e.getValue().equals(keyToRemove));

        // Add current name mapping
        String currentName = machine.getName();
        if (currentName == null) currentName = "";
        nameToKey.put(currentName, foundKey);
        return foundKey;
    }
}
