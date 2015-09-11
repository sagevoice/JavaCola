package edu.monash.infotech.marvl.cola.powergraph;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PowerEdge {

    public Object source;
    public Object target;
    public int type;

    public Object get(final String key) {
        if ("source".equals(key)) {
            return this.source;
        } else if ("target".equals(key)) {
            return this.target;
        }
        return null;
    }

    public void set(final String key, final Object value) {
        if ("source".equals(key)) {
            this.source = value;
        } else if ("target".equals(key)) {
            this.target = value;
        }
    }

}
