package edu.monash.infotech.marvl.cola.geom;

import java.util.Arrays;
import java.util.List;

public class BiTangents {

    public BiTangent rl;
    public BiTangent lr;
    public BiTangent ll;
    public BiTangent rr;

    public List<BiTangent> values() {
        return Arrays.asList(rl, lr, ll, rr);
    }

}
