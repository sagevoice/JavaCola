package edu.monash.infotech.marvl.cola;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LongestCommonSubsequence<T> extends Match {

    public boolean      reversed;
    public ArrayList<T> s;
    public ArrayList<T> t;

    LongestCommonSubsequence(final ArrayList<T> s, final ArrayList<T> t) {
        super();
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.s = s;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.t = t;
        final Match mf = this.findMatch(s, t);
        final ArrayList<T> tr = (ArrayList<T>)t.clone();
        Collections.reverse(tr);
        final Match mr = this.findMatch(s, tr);
        if (mf.length >= mr.length) {
            this.length = mf.length;
            this.si = mf.si;
            this.ti = mf.ti;
            this.reversed = false;
        } else {
            this.length = mr.length;
            this.si = mr.si;
            this.ti = t.size() - mr.ti - mr.length;
            this.reversed = true;
        }
    }

    private Match findMatch(final ArrayList<T> s, final ArrayList<T> t) {
        final int m = s.size();
        final int n = t.size();
        final Match match = new Match(0, -1, -1);
        int[][] l = new int[m][0];
        for (int i = 0; i < m; i++) {
            l[i] = new int[n];
            for (int j = 0; j < n; j++) {
                if (s.get(i) == t.get(j)) {
                    int v = l[i][j] = (i == 0 || j == 0) ? 1 : l[i - 1][j - 1] + 1;
                    if (v > match.length) {
                        match.length = v;
                        match.si = i - v + 1;
                        match.ti = j - v + 1;
                    }
                } else {
                    l[i][j] = 0;
                }
            }
        }
        return match;
    }

    public List<T> getSequence() {
        return (0 <= this.length) ? this.s.subList(this.si, this.si + this.length) : new ArrayList<>();
    }
}
