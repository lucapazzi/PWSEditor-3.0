package assembly;

import editor.StateMachinePanel;
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
    // Optional alias data for each machine (UI-only metadata)
    private Map<String, StateMachinePanel.AliasData> aliasDataByKey = new LinkedHashMap<>();

    /**
     * Creates an empty machine library.
     */
    public MachineLibrary() {
    }

    /**
     * Adds a machine and generates a unique key.
     *
     * @param m machine to add
     * @return generated key, or null if machine is null
     */
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

    /**
     * Adds a machine and generates a unique key, optionally storing alias data.
     *
     * @param m machine to add
     * @param aliasData alias metadata (may be null)
     * @return generated key, or null if machine is null
     */
    public String addMachine(StateMachine m, StateMachinePanel.AliasData aliasData) {
        if (m == null) return null;
        String name = m.getName();
        if (name == null) name = "";
        if (nameToKey.containsKey(name)) {
            return nameToKey.get(name);
        }
        String key = UUID.randomUUID().toString();
        machines.put(key, m);
        nameToKey.put(name, key);
        if (aliasData != null) {
            aliasDataByKey.put(key, aliasData);
        }
        return key;
    }

    /**
     * Adds a machine with an explicit key.
     *
     * @param key machine key
     * @param m machine to add
     */
    public void addMachine(String key, StateMachine m) {
        if (m == null || key == null) return;
        machines.put(key, m);
        String name = m.getName();
        if (name == null) name = "";
        nameToKey.put(name, key);
    }

    /**
     * Adds a machine with an explicit key and optional alias data.
     *
     * @param key machine key
     * @param m machine to add
     * @param aliasData alias metadata (may be null)
     */
    public void addMachine(String key, StateMachine m, StateMachinePanel.AliasData aliasData) {
        addMachine(key, m);
        if (key == null) return;
        if (aliasData != null) {
            aliasDataByKey.put(key, aliasData);
        } else {
            aliasDataByKey.remove(key);
        }
    }

    /**
     * Returns the machine for the given key.
     *
     * @param key machine key
     * @return machine or null if not found
     */
    public StateMachine get(String key) {
        return machines.get(key);
    }

    /**
     * Removes a machine by key.
     *
     * @param key machine key
     */
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
        if (key != null) {
            aliasDataByKey.remove(key);
        }
    }

    /**
     * Rename the machine identified by key to newName.
     * Returns true if rename succeeded, false if newName is already used or key not found.
     *
     * @param key machine key
     * @param newName new machine name
     * @return true if rename succeeded
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

    /**
     * Returns the map of machines by key.
     *
     * @return map of machines by key
     */
    public Map<String, StateMachine> getMachines() {
        return machines;
    }

    /**
     * Clear all machines and name mappings.
     */
    public void clear() {
        machines.clear();
        nameToKey.clear();
        aliasDataByKey.clear();
    }

    /**
     * Returns the key for a machine name.
     *
     * @param name machine name
     * @return key or null if not found
     */
    public String getKeyByName(String name) {
        if (name == null) name = "";
        return nameToKey.get(name);
    }

    /**
     * Returns a machine by name.
     *
     * @param name machine name
     * @return machine or null if not found
     */
    public StateMachine getByName(String name) {
        String k = getKeyByName(name);
        return k != null ? get(k) : null;
    }

    /**
     * Returns the set of known machine names.
     *
     * @return set of known machine names
     */
    public java.util.Set<String> getNames() {
        return nameToKey.keySet();
    }

    /**
     * Returns alias data for a machine key.
     *
     * @param key machine key
     * @return alias data or null if none
     */
    public StateMachinePanel.AliasData getAliasData(String key) {
        if (key == null) return null;
        return aliasDataByKey.get(key);
    }

    /**
     * Sets alias data for a machine key.
     *
     * @param key machine key
     * @param data alias data (null to clear)
     */
    public void setAliasData(String key, StateMachinePanel.AliasData data) {
        if (key == null) return;
        if (data == null) {
            aliasDataByKey.remove(key);
        } else {
            aliasDataByKey.put(key, data);
        }
    }

    /**
     * Synchronize the nameToKey mapping for a machine instance.
     * Call this after external code changes a machine's name directly.
     * Returns the key if found, null otherwise.
     *
     * @param machine machine instance
     * @return key for the machine, or null if not found
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
