package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.GraphNode;

import org.testng.annotations.*;

public class Tests {
     private double nodeDistance(final GraphNode u, final GraphNode v) {
        final double dx = u.x - v.x, dy = u.y - v.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean approxEquals(final double actual, final double expected, final double threshold) {
        return Math.abs(actual - expected) <= threshold;
    }

    @Test(description="small power-graph")
    public void smallPowerGraphTest () {
        d3.json("../examples/graphdata/n7e23.json", function (error, graph) {
            var n = graph.nodes.length;
            ok(n == 7);
            var linkAccessor = {
                getSourceIndex: function (e) { return e.source },
                getTargetIndex: function (e) { return e.target },
                getType: function(e) { return 0 },
                makeLink: function (u, v) { return { source: u, target: v } }
            };
            var c = new cola.powergraph.Configuration(n, graph.links, linkAccessor);
            ok(c.modules.length == 7);
            var es;
            ok(c.R == (es = c.allEdges()).length, "c.R=" + c.R + ", actual edges in c=" + es.length);
            var m = c.merge(c.modules[0], c.modules[4]);
            ok(m.children.contains(0));
            ok(m.children.contains(4));
            ok(m.outgoing.contains(1));
            ok(m.outgoing.contains(3));
            ok(m.outgoing.contains(5));
            ok(m.outgoing.contains(6));
            ok(m.incoming.contains(2));
            ok(m.incoming.contains(5));
            ok(c.R == (es = c.allEdges()).length, "c.R=" + c.R + ", actual edges in c=" + es.length);
            m = c.merge(c.modules[2], c.modules[3]);
            ok(c.R == (es = c.allEdges()).length, "c.R=" + c.R + ", actual edges in c=" + es.length);

            c = new cola.powergraph.Configuration(n, graph.links, linkAccessor);
            var lastR = c.R;
            while (c.greedyMerge()) {
                ok(c.R < lastR);
                lastR = c.R;
            }
            var finalEdges = [];
            var powerEdges = c.allEdges();
            ok(powerEdges.length == 7);
            var groups = c.getGroupHierarchy(finalEdges);
            ok(groups.length == 4);
            start();
        });
        ok(true);
    }

    @Test(description="all-pairs shortest paths")
    public void allPairsShortestPathsTest() {
        var d3cola = cola.d3adaptor();

        d3.json("../examples/graphdata/triangle.json", function (error, graph) {
            d3cola
                .nodes(graph.nodes)
                .links(graph.links)
                .linkDistance(1);
            var n = d3cola.nodes().length;
            equal(n, 4);
            var getSourceIndex = function (e) {
                return e.source;
            }
            var getTargetIndex = function (e) {
                return e.target;
            }
            var getLength = function (e) {
                return 1;
            }
            var D = (new cola.shortestpaths.Calculator(n, d3cola.links(), getSourceIndex, getTargetIndex, getLength)).DistanceMatrix();
            deepEqual(D, [
                [0, 1, 1, 2],
                [1, 0, 1, 2],
                [1, 1, 0, 1],
                [2, 2, 1, 0],
            ]);
            var x = [0, 0, 1, 1], y = [1, 0, 0, 1];
            var descent = new cola.Descent([x, y], D);
            var s0 = descent.reduceStress();
            var s1 = descent.reduceStress();
            ok(s1 < s0);
            var s2 = descent.reduceStress();
            ok(s2 < s1);
            d3cola.start(0,0,10);
            var lengths = graph.links.map(function (l) {
                var u = l.source, v = l.target,
                    dx = u.x - v.x, dy = u.y - v.y;
                return Math.sqrt(dx*dx + dy*dy);
            }), avg = function (a) { return a.reduce(function (u, v) { return u + v }) / a.length },
                mean = avg(lengths),
                variance = avg(lengths.map(function (l) { var d = mean - l; return d * d; }));
            ok(variance < 0.1);
            start();
        });
        ok(true);
    }

    @Test(description="edge lengths")
    public void edgeLengthsTest () {
        var d3cola = cola.d3adaptor();

        d3.json("../examples/graphdata/triangle.json", function (error, graph) {
            var length = function (l) {
                return cola.Layout.linkId(l) == "2-3" ? 2 : 1;
            }
            d3cola
                .linkDistance(length)
                .nodes(graph.nodes)
                .links(graph.links);
            d3cola.start(100);
            var errors = graph.links.map(function (e) {
                var l = nodeDistance(e.source, e.target);
                return Math.abs(l - length(e));
            }), max = Math.max.apply(this, errors);
            ok(max < 0.1, "max = "+max);
            start();
        });
        ok(true);
    }

    @Test(description="group")
    public void groupTest () {
        var d3cola = cola.d3adaptor();

        var length = function (l) {
            return d3cola.linkId(l) == "2-3" ? 2 : 1;
        }
        var nodes = [];
        var u = { x: -5, y: 0, width: 10, height: 10 };
        var v = { x: 5, y: 0, width: 10, height: 10 };
        var g = { padding: 10, leaves: [0] };

        d3cola
            .linkDistance(length)
            .avoidOverlaps(true)
            .nodes([u,v])
            .groups([g]);
        d3cola.start(10, 10, 10);

        ok(approxEquals(g.bounds.width(), 30, 0.1));
        ok(approxEquals(g.bounds.height(), 30, 0.1));

        ok(approxEquals(Math.abs(u.y - v.y), 20, 0.1));
    }

    @Test(description="equality constraints")
    public void equalityConstraintsTest() {
        var d3cola = cola.d3adaptor();

        d3.json("../examples/graphdata/triangle.json", function (error, graph) {
            d3cola
                .nodes(graph.nodes)
                .links(graph.links)
                .constraints([{
                    type: "separation", axis: "x",
                    left: 0, right: 1, gap: 0, equality: true
                }, {
                    type: "separation", axis: "y",
                    left: 0, right: 2, gap: 0, equality: true
                }]);
            d3cola.start(20, 20, 20);
            ok(Math.abs(graph.nodes[0].x - graph.nodes[1].x) < 0.001);
            ok(Math.abs(graph.nodes[0].y - graph.nodes[2].y) < 0.001);
            start();
        });
        ok(true);
    }

    @Test(description="convex hulls")
    public void convexHullsTest() {
        var rand = new cola.PseudoRandom();
        var nextInt = function (r) { return Math.round(rand.getNext() * r) }
        var width = 100, height = 100;

        for (var k = 0; k < 10; ++k) {
            var P = [];
            for (var i = 0; i < 5; ++i) {
                var p;
                P.push(p = { x: nextInt(width), y: nextInt(height) });
            }
            var h = cola.geom.ConvexHull(P);

            for (var i = 2; i < h.length; ++i) {
                var p = h[i - 2], q = h[i - 1], r = h[i];
                ok(cola.geom.isLeft(p, q, r) >= 0, "clockwise hull " + i);
                for (var j = 0; j < P.length; ++j) {
                    ok(cola.geom.isLeft(p, q, P[j]) >= 0, "" + j);
                }
            }
            ok(h[0] !== h[h.length - 1], "first and last point of hull are different" + k);
        }
    }

    @Test(description="radial sort")
    public void radialSortTest() {
        var n = 100;
        var width = 400, height = 400;
        var P = [];
        var x = 0, y = 0;
        var rand = new cola.PseudoRandom(5);
        var nextInt = function (r) { return Math.round(rand.getNext() * r) }
        for (var i = 0; i < n; ++i) {
            var p;
            P.push(p = { x: nextInt(width), y: nextInt(height) });
            x += p.x; y += p.y;
        }
        var q = { x: x / n, y: y / n };
        //console.log(q);
        var p0 = null;
        cola.geom.clockwiseRadialSweep(q, P, function (p, i) {
            if (p0) {
                var il = cola.geom.isLeft(q, p0, p);
                ok(il >= 0);
            }
            p0 = p;
        });
    }

    function makePoly(rand) {
        var length = function (p, q) { var dx = p.x - q.x, dy = p.y - q.y; return dx * dx + dy * dy; };
        var nextInt = function (r) { return Math.round(rand.getNext() * r) }
        var n = nextInt(7) + 3, width = 10, height = 10;
        if (arguments.length > 1) {
            width = arguments[1];
            height = arguments[2];
        }
        var P = [];
        loop: for (var i = 0; i < n; ++i) {
            var p = { x: nextInt(width), y: nextInt(height) };
            var ctr = 0;
            while (i > 0 && length(P[i - 1], p) < 1 // min segment length is 1
                || i > 1 && ( // new point must keep poly convex
                        cola.geom.isLeft(P[i - 2], P[i - 1], p) <= 0
                    || cola.geom.isLeft(P[i - 1], p, P[0]) <= 0
                    || cola.geom.isLeft(p, P[0], P[1]) <= 0)) {
                if (ctr++ > 10) break loop; // give up after ten tries (maybe not enough space left for another convex point)
                p = { x: nextInt(width), y: nextInt(height) };
            }
            P.push(p);
        }
        if (P.length > 2) { // must be at least triangular
            P.push({ x: P[0].x, y: P[0].y });
            return P;
        }
        return makePoly(rand, width, height);
    }

    function makeNonoverlappingPolys(rand, n) {
        var P = [];
        var nextInt = function (r) { return Math.round(rand.getNext() * r) }
        var overlaps = function (p) {
            for (var i = 0; i < P.length; i++) {
                var q = P[i];
                if (cola.geom.polysOverlap(p, q)) return true;
            }
            return false;
        }
        for (var i = 0; i < n; i++) {
            var p = makePoly(rand);
            while (overlaps(p)) {
                var dx = nextInt(10) - 5, dy = nextInt(10) - 5;
                p.forEach(function (p) { p.x += dx; p.y += dy; });
            }
            P.push(p);
        }
        var minX = 0, minY = 0;
        P.forEach(function (p) { p.forEach(function (p) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
            })
        });
        P.forEach(function (p) {
            p.forEach(function (p) { p.x -= minX; p.y -= minY; });
        });
        return P;
    }

    function midPoint(p) {
        var mx = 0, my = 0;
        var n = p.length - 1;
        for (var i = 0; i < n; i++) {
            var q = p[i];
            mx += q.x;
            my += q.y;
        }
        return { x: mx/n, y: my/n };
    }

    @Test(description="metro crossing min")
    public void metroCrossingMinTest () {
        function countRouteIntersections(routes) {
            var ints = [];
            for (var i = 0; i < routes.length - 1; i++) {
                for (var j = i + 1; j < routes.length ; j++) {
                    var r1 = routes[i], r2 = routes[j];
                    r1.forEach(function (s1) {
                        r2.forEach(function (s2) {
                            var int = cola.vpsc.Rectangle.lineIntersection(s1[0].x, s1[0].y, s1[1].x, s1[1].y, s2[0].x, s2[0].y, s2[1].x, s2[1].y);
                            if (int) ints.push(int);
                        })
                    })
                }
            }
            return ints.length;
        }
        var verts, edges, order, routes;
        function makeInstance() {
            verts.forEach(function (v, i) {
                v.id = i;
                v.edges = {};
            });
            edges.forEach(function (e, i) {
                e.id = i;
            });
        }
        function twoParallelSegments() {
            verts = [
                { x: 0, y: 10 },
                { x: 10, y: 10 }
            ];
            edges = [
                [verts[0], verts[1]],
                [verts[0], verts[1]]
            ];
            makeInstance();
        }
        function threeByThreeSegments() {
            verts = [
                { x: 0, y: 10 },
                { x: 10, y: 10 },
                { x: 10, y: 20 },
                { x: 10, y: 30 },
                { x: 20, y: 20 },
                { x: 10, y: 0 },
                { x: 0, y: 20 }
            ];
            edges = [
                [verts[0], verts[1], verts[2], verts[3]],
                [verts[0], verts[1], verts[2], verts[4]],
                [verts[5], verts[1], verts[2], verts[6]]
            ];
            makeInstance();
        }
        function regression1() {
            verts = [
                {x:430.79999999999995, y:202.5},
                {x:464.4, y:202.5},
                {x:464.4, y:261.6666666666667},
                {x:464.4, y:320.83333333333337},
                {x:474, y:320.83333333333337},
                {x:486, y:320.83333333333337},
                {x:498.0000000000001, y:202.5},
                {x:474, y:202.5},
            ];
            verts.forEach(function(v) {
                v.x -= 400;
                v.y -= 160;
                v.x /= 4;
                v.y /= 8;
            });
            edges = [[
                verts[0],
                verts[1],
                verts[2],
                verts[3],
                verts[4],
                verts[5]
                ], [
                verts[6],
                verts[7],
                verts[1],
                verts[0]
                ]];
            makeInstance();
        }
        function nudge() {
            order = cola.GridRouter.orderEdges(edges);
            routes = edges.map(function (e) { return cola.GridRouter.makeSegments(e); });
            cola.GridRouter.nudgeSegments(routes, 'x', 'y', order, 2);
            cola.GridRouter.nudgeSegments(routes, 'y', 'x', order, 2);
            cola.GridRouter.unreverseEdges(routes, edges);
        }

        // trivial case
        twoParallelSegments();
        nudge();
        // two segments, one reversed
        edges[1].reverse();
        nudge();

        threeByThreeSegments();
        var lcs = new cola.LongestCommonSubsequence('ABAB'.split(''), 'DABA'.split(''));
        deepEqual(lcs.getSequence(), 'ABA'.split(''));
        lcs = new cola.LongestCommonSubsequence(edges[0], edges[1]);
        equal(lcs.length, 3);
        deepEqual(lcs.getSequence().map(function (v) { return v.id }), [0, 1, 2]);
        var e0reversed = edges[0].slice(0).reverse();
        lcs = new cola.LongestCommonSubsequence(e0reversed, edges[1]);
        deepEqual(lcs.getSequence().map(function (v) { return v.id }), [2, 1, 0]);
        ok(lcs.reversed);

        nudge();
        equal(routes[0].length, 2);
        equal(routes[1].length, 3);
        equal(routes[2].length, 2);

        equal(countRouteIntersections(routes), 2);

        // flip it in y and try again
        threeByThreeSegments();
        verts.forEach(function (v) { v.y = 30 - v.y });
        nudge();
        equal(countRouteIntersections(routes), 2);

        // reverse the first edge path and see what happens
        threeByThreeSegments();
        edges[0].reverse();
        nudge();
        equal(countRouteIntersections(routes), 2);

        // reverse the second edge path
        threeByThreeSegments();
        edges[1].reverse();
        nudge();
        equal(countRouteIntersections(routes), 2);

        // reverse the first 2 edge paths
        threeByThreeSegments();
        edges[0].reverse();
        edges[1].reverse();
        nudge();
        equal(countRouteIntersections(routes), 2);

        regression1();
        nudge();
        equal(countRouteIntersections(routes), 0);
    }

    // next steps:
    //  o label node and group centre and boundary vertices
    //  - non-traversable regions (obstacles) are determined by finding the highest common ancestors of the source and target nodes
    //  - to route each edge the weights of the edges are adjusted such that those inside obstacles
    //    have infinite weight while those inside the source and target node have zero weight
    //  - augment dijkstra with a cost for bends
    @Test(description="grid router")
    public void gridRouterTest() {
        d3.json("../examples/graphdata/tetrisbugmultiedgeslayout.json", function (error, graph) {
            var gridrouter = new cola.GridRouter(graph.nodes,{
                getChildren: function(v) {
                    return v.children;
                },
                getBounds: function(v) {
                    return typeof v.bounds !== 'undefined'
                        ? new cola.vpsc.Rectangle(v.bounds.x, v.bounds.X, v.bounds.y, v.bounds.Y)
                        : undefined;
                }
            });
            var source = 1, target = 2;
            var shortestPath = gridrouter.route(source, target);
            function check(expected) {
                ok(gridrouter.obstacles.length === expected);
                ok(gridrouter.obstacles.map(function (v) { return v.id }).indexOf(source) < 0);
                ok(gridrouter.obstacles.map(function (v) { return v.id }).indexOf(target) < 0);
            }
            check(8);

            source = 0, target = 7;
            shortestPath = gridrouter.route(source, target);
            check(6);

            source = 4, target = 5;
            shortestPath = gridrouter.route(source, target);
            check(8);

            source = 11, target = 2;
            shortestPath = gridrouter.route(source, target);
            check(13);

            // group to node
            source = 16, target = 5;
            shortestPath = gridrouter.route(source, target);
            check(7);

            // bend minimal?
            source = 1, target = 2;
            shortestPath = gridrouter.route(source, target);

            start();
        });
        ok(true);
    }

    @Test(description="shortest path with bends")
    public void shortestPathWithBendsTest() {
        //  0 - 1 - 2
        //      |   |
        //      3 - 4
        var nodes = [[0,0],[1,0],[2,0],[1,1],[2,1]];
        var edges = [[0,1,1],[1,2,2],[1,3,1],[3,4,1],[2,4,2]];
        function source(e) { return e[0]};
        function target(e) { return e[1]};
        function length(e) { return e[2]};
        var sp = new cola.shortestpaths.Calculator(nodes.length, edges, source, target, length);
        var path = sp.PathFromNodeToNodeWithPrevCost(0, 4,
        function (u,v,w){
            var a = nodes[u], b = nodes[v], c = nodes[w];
            var dx = Math.abs(c[0] - a[0]), dy = Math.abs(c[1] - a[1]);
            return dx > 0.01 && dy > 0.01
                ? 1000
                : 0;
        });
        ok(true);
    }

    @Test(description="tangent visibility graph")
    public void tangentVisibilityGraphTest() {
        for (var tt = 0; tt < 100; tt++) {
            var rand = new cola.PseudoRandom(tt),
                nextInt = function (r) { return Math.round(rand.getNext() * r) },
                n = 10,
                P = makeNonoverlappingPolys(rand, n),
                port1 = midPoint(P[8]),
                port2 = midPoint(P[9]),
                g = new cola.geom.TangentVisibilityGraph(P),
                start = g.addPoint(port1, 8),
                end = g.addPoint(port2, 9);
            g.addEdgeIfVisible(port1, port2, 8, 9);
            var getSource = function (e) { return e.source.id }, getTarget = function(e) { return e.target.id}, getLength = function(e) { return e.length() }
            shortestPath = (new cola.shortestpaths.Calculator(g.V.length, g.E, getSource, getTarget, getLength)).PathFromNodeToNode(start.id, end.id);
            ok(shortestPath.length > 0);
        }
        ok(true);
    }

    @Test(description="tangents")
    public void tangentsTest() {
        var rand = new cola.PseudoRandom();
        var rect = [{ x: 10, y: 10 }, { x: 20, y: 10 }, { x: 10, y: 20 }, { x: 20, y: 20 }];
        var pnt = [{ x: 0, y: 0 }];
        var t1 = cola.geom.tangents(pnt, rect);
        for (var j = 0; j < 100; j++) {
            var A = makePoly(rand), B = makePoly(rand);
            B.forEach(function (p) { p.x += 11 });
            //if (j !== 207) continue;
            var t = cola.geom.tangents(A, B);
            // ok(t.length === 4, t.length + " tangents found at j="+j);
        }
        ok(true);
    }

    function intersects(l, P) {
        var ints = [];
        for (var i = 1; i < P.length; ++i) {
            var int = cola.vpsc.Rectangle.lineIntersection(
                l.x1, l.y1,
                l.x2, l.y2,
                P[i-1].x, P[i-1].y,
                P[i].x, P[i].y
                );
            if (int) ints.push(int);
        }
        return ints;
    }

    @Test(description="pseudo random number test")
    public void pseudoRandomNumberTest() {
        var rand = new cola.PseudoRandom();
        for (var i = 0; i < 100; ++i) {
            var r = rand.getNext();
            ok(r <= 1, "r=" + r);
            ok(r >= 0, "r=" + r);
            r = rand.getNextBetween(5, 10);
            ok(r <= 10, "r=" + r);
            ok(r >= 5, "r=" + r);
            r = rand.getNextBetween(-5, 0);
            ok(r <= 0, "r=" + r);
            ok(r >= -5, "r=" + r);
            //console.log(r);
        }
    }

    @Test(description="rectangle intersections")
    public void rectangleIntersectionsTest() {
        var r = new cola.vpsc.Rectangle(2, 4, 0, 2);
        var p = r.rayIntersection(0, 1);
        ok(p.x == 2);
        ok(p.y == 1);
        p = r.rayIntersection(0, 0);
        ok(p.x == 2);
    }

    @Test(description="matrix perf test")
    public void matrixPerfTest() {
        ok(true); return; // disable

        var now = window.performance ? function () { return window.performance.now(); } : function () { };
        console.log("Array test:");
        var startTime = now();
        var totalRegularArrayTime = 0;
        var repeats = 1000;
        var n = 100;
        var M;
        for (var k = 0; k < repeats; ++k) {
            M = new Array(n);
            for (var i = 0; i < n; ++i) {
                M[i] = new Array(n);
            }
        }

        var t = now() - startTime;
        console.log("init = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            for (var i = 0; i < n; ++i) {
                for (var j = 0; j < n; ++j) {
                    M[i][j] = 1;
                }
            }
        }

        var t = now() - startTime;
        console.log("write array = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            var sum = 0;
            for (var i = 0; i < n; ++i) {
                for (var j = 0; j < n; ++j) {
                    sum += M[i][j];
                }
            }
            //equal(sum, n * n);
        }
        var t = now() - startTime;
        console.log("read array = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        ok(true);

        var totalTypedArrayTime = 0;
        console.log("Typed Array test:");
        var startTime = now();
        for (var k = 0; k < repeats; ++k) {
            MT = new Float32Array(n * n);
        }

        var t = now() - startTime;
        console.log("init = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            for (var i = 0; i < n * n; ++i) {
                MT[i] = 1;
            }
        }
        var t = now() - startTime;
        console.log("write array = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            var sum = 0;
            for (var i = 0; i < n * n; ++i) {
                sum += MT[i];
            }
            //equal(sum, n * n);
        }
        var t = now() - startTime;
        console.log("read array = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        ok(isNaN(totalRegularArrayTime) || totalRegularArrayTime < totalTypedArrayTime, "totalRegularArrayTime=" + totalRegularArrayTime + " totalTypedArrayTime="+totalTypedArrayTime+" - if this consistently fails then maybe we should switch to typed arrays");
    }

    @Test(description="priority queue test")
    public void priorityQueueTest() {
        var q = new PriorityQueue(function (a, b) { return a <= b; });
        q.push(42, 5, 23, 5, Math.PI);
        var u = Math.PI, v;
        strictEqual(u, q.top());
        var cnt = 0;
        while ((v = q.pop()) !== null) {
            ok(u <= v);
            u = v;
            ++cnt;
        }
        equal(cnt, 5);
        q.push(42, 5, 23, 5, Math.PI);
        var k = q.push(13);
        strictEqual(Math.PI, q.top());
        q.reduceKey(k, 2);
        u = q.top();
        strictEqual(u, 2);
        cnt = 0;
        while ((v = q.pop()) !== null) {
            ok(u <= v);
            u = v;
            ++cnt;
        }
        equal(cnt, 6);
    }

    @Test(description="dijkstra")
    public void dijkstraTest() {
        // 0  4-3
        //  \/ /
        //  1-2
        var n = 5;
        var links = [[0, 1], [1, 2], [2, 3], [3, 4], [4, 1]],
            getSource = function (l) { return l[0] }, getTarget = function (l) { return l[1] }, getLength = function(l) { return 1 }
        var calc = new cola.shortestpaths.Calculator(n, links, getSource, getTarget, getLength);
        var d = calc.DistancesFromNode(0);
        deepEqual(d, [0, 1, 2, 3, 2]);
        var D = calc.DistanceMatrix();
        deepEqual(D, [
            [0, 1, 2, 3, 2],
            [1, 0, 1, 2, 1],
            [2, 1, 0, 1, 2],
            [3, 2, 1, 0, 1],
            [2, 1, 2, 1, 0]
        ]);
    }

    @Test(description="vpsc")
    public void vpscTest() {
        var round = function (v, p) {
            var m = Math.pow(10, p);
            return Math.round(v * m) / m;
        };
        var rnd = function (a, p) {
            if (typeof p === "undefined") { p = 4; }
            return a.map(function (v) { return round(v, p) })
        };
        var res = function (a, p) {
            if (typeof p === "undefined") { p = 4; }
            return a.map(function (v) { return round(v.position(), p) })
        };
        vpsctestcases.forEach(function (t) {
            var vs = t.variables.map(function (u, i) {
                var v;
                if (typeof u === "number") {
                    v = new cola.vpsc.Variable(u);
                } else {
                    v = new cola.vpsc.Variable(u.desiredPosition, u.weight, u.scale);
                }
                v.id = i;
                return v;
            });
            var cs = t.constraints.map(function (c) {
                return new cola.vpsc.Constraint(vs[c.left], vs[c.right], c.gap);
            });
            var solver = new cola.vpsc.Solver(vs, cs);
            solver.solve();
            if (typeof t.expected !== "undefined") {
                deepEqual(rnd(t.expected, t.precision), res(vs, t.precision), t.description);
            }
        });
    }

    @Test(description="rbtree")
    public void rbtreeTest() {
        var tree = new cola.vpsc.RBTree(function (a, b) { return a - b });
        var data = [5, 8, 3, 1, 7, 6, 2];
        data.forEach(function (d) { tree.insert(d); });
        var it = tree.iterator(), item;
        var prev = 0;
        while ((item = it.next()) !== null) {
            ok(prev < item);
            prev = item;
        }

        var m = tree.findIter(5);
        ok(m.prev(3));
        ok(m.next(6));
    }

    @Test(description="overlap removal")
    public void overlapRemovalTest() {
        var rs = [
            new cola.vpsc.Rectangle(0, 2, 0, 1),
            new cola.vpsc.Rectangle(1, 3, 0, 1)
        ];
        equal(rs[0].overlapX(rs[1]), 1);
        equal(rs[0].overlapY(rs[1]), 1);
        var vs = rs.map(function (r) {
            return new cola.vpsc.Variable(r.cx());
        });
        var cs = cola.vpsc.generateXConstraints(rs, vs);
        equal(cs.length, 1);
        var solver = new cola.vpsc.Solver(vs, cs);
        solver.solve();
        vs.forEach(function(v, i) {
            rs[i].setXCentre(v.position());
        });
        equal(rs[0].overlapX(rs[1]), 0);
        equal(rs[0].overlapY(rs[1]), 1);

        vs = rs.map(function (r) {
            return new cola.vpsc.Variable(r.cy());
        });
        cs = cola.vpsc.generateYConstraints(rs, vs);
        equal(cs.length, 0);
    }

    @Test(description="cola.vpsc.removeOverlaps")
    public void removeOverlapsTest() {
        var overlaps = function (rs) {
            var cnt = 0;
            for (var i = 0, n = rs.length; i < n - 1; ++i) {
                var r1 = rs[i];
                for (var j = i + 1; j < n; ++j) {
                    var r2 = rs[j];
                    if (r1.overlapX(r2) > 0 && r1.overlapY(r2) > 0) {
                        cnt++;
                    }
                }
            }
            return cnt;
        };
        var rs = [
            new cola.vpsc.Rectangle(0, 4, 0, 4),
            new cola.vpsc.Rectangle(3, 5, 1, 2),
            new cola.vpsc.Rectangle(1, 3, 3, 5)
        ];
        equal(overlaps(rs), 2);
        cola.vpsc.removeOverlaps(rs);
        equal(overlaps(rs), 0);
        equal(rs[1].y, 1);
        equal(rs[1].Y, 2);

        rs = [
            new cola.vpsc.Rectangle(148.314,303.923,94.4755,161.84969999999998),
            new cola.vpsc.Rectangle(251.725,326.6396,20.0193,69.68379999999999),
            new cola.vpsc.Rectangle(201.235,263.6349,117.221,236.923),
            new cola.vpsc.Rectangle(127.445,193.7047,46.5891,186.5991),
            new cola.vpsc.Rectangle(194.259,285.7201,204.182,259.13239999999996)
        ];
        cola.vpsc.removeOverlaps(rs);
        equal(overlaps(rs), 0);
    }

    @Test(description="packing")
    public void packingTest() {
        var nodes = []
        for (var i = 0; i < 9; i++) { nodes.push({width: 10, height: 10}) }
        cola.d3adaptor().nodes(nodes).start();
        var check = function (aspectRatioThreshold) {
            var dim = nodes.reduce(function (p, v) {
                return {
                    x: Math.min(v.x - v.width / 2, p.x),
                    y: Math.min(v.y - v.height / 2, p.y),
                    X: Math.max(v.x + v.width / 2, p.X),
                    Y: Math.max(v.y + v.height / 2, p.Y)
                };
            }, { x: Number.POSITIVE_INFINITY, X: Number.NEGATIVE_INFINITY, y: Number.POSITIVE_INFINITY, Y: Number.NEGATIVE_INFINITY });
            var width = dim.X - dim.x, height = dim.Y - dim.y;
            ok(Math.abs(width / height - 1) < aspectRatioThreshold);
        }
        check(0.001);

        // regression test, used to cause infinite loop
        nodes = [{ width: 24, height: 35 }, { width: 24, height: 35 }, { width: 32, height: 35 }];
        cola.d3adaptor().nodes(nodes).start();
        check(0.3);

        // for some reason the first rectangle is offset by the following - no assertion for this yet.
        var rand = new cola.PseudoRandom(51);
        for (var i = 0; i < 19; i++) { nodes.push({ width: rand.getNextBetween(5, 30), height: rand.getNextBetween(5, 30) }) }
        cola.d3adaptor().nodes(nodes).avoidOverlaps(false).start();
        check(0.1);
    }
}
