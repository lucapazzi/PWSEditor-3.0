package assembly;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

/** Serializable list of actions associated with a transition. */
public class ActionList extends ArrayList<Action> implements Serializable {

    /** Creates an empty action list. */
    public ActionList() {
        super();
    }

    /**
     * Creates an action list initialized with the given collection.
     *
     * @param c initial actions
     */
    public ActionList(Collection<? extends Action> c) {
        super(c);
    }

    @Override
    public String toString() {
        if (this.isEmpty()) {
            return "〈 〉";
        }
        StringBuilder sb = new StringBuilder("〈 ");
        for (int i = 0; i < this.size(); i++) {
            sb.append(this.get(i).toString());
            if (i < this.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(" 〉");
        return sb.toString();
    }
}
