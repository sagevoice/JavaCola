package edu.monash.infotech.marvl.cola;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LongestCommonSubsequence<T> extends Match {

    public boolean reversed;
    public List<T> s;
    public List<T> t;

    LongestCommonSubsequence(final List<T> s, final List<T> t) {
        super();
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.s = s;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.t = t;
        final Match mf = findMatch(s, t);
        final List<T> tr = new ArrayList<>(t);
        Collections.reverse(tr);
        final Match mr = findMatch(s, tr);
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

    private static <T> Match findMatch(final List<T> s, final List<T> t) {
        final int m = s.size();
        final int n = t.size();
        final Match match = new Match(0, -1, -1);
        int[][] l = new int[m][0];
        for (int i = 0; i < m; i++) {
            l[i] = new int[n];
            for (int j = 0; j < n; j++) {
                if (s.get(i) == t.get(j)) {
                    final int v = (0 == i || 0 == j) ? 1 : l[i - 1][j - 1] + 1;
                    l[i][j] = v;
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
