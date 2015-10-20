define(['can', 'jquery', 'bootstrap', 'routes' ], function (can, $, b, routes) {
    (function() {
        var e = can.List._bubbleRule;
        can.List._bubbleRule = function(t, n) {
            var r = e.apply(this, arguments);
            return n.comparator && can.inArray("change", r) === -1 && r.push("change"),
            r
        };
        var t = can.List.prototype
          , n = t._changes
          , r = t.setup
          , i = t.unbind;
        can.extend(t, {
            setup: function(e, t) {
                r.apply(this, arguments),
                this._comparatorBound = !1,
                this._init = 1,
                this.bind("comparator", can.proxy(this._comparatorUpdated, this)),
                delete this._init,
                this.comparator && this.sort()
            },
            _comparatorUpdated: function(e, t) {
                t || t === 0 ? (this.sort(),
                this._bindings > 0 && !this._comparatorBound && this.bind("change", this._comparatorBound = function() {}
                )) : this._comparatorBound && (i.call(this, "change", this._comparatorBound),
                this._comparatorBound = !1)
            },
            unbind: function(e, t) {
                var n = i.apply(this, arguments);
                return this._comparatorBound && this._bindings === 1 && (i.call(this, "change", this._comparatorBound),
                this._comparatorBound = !1),
                n
            },
            _comparator: function(e, t) {
                var n = this.comparator;
                return n && typeof n == "function" ? n(e, t) : e === t ? 0 : e < t ? -1 : 1
            },
            _changes: function(e, t, r, i, s) {
                if (this.comparator && /^\d+/.test(t)) {
                    if (e.batchNum && e.batchNum !== this._lastBatchNum) {
                        this.sort(),
                        this._lastBatchNum = e.batchNum;
                        return
                    }
                    var o = +/^\d+/.exec(t)[0]
                      , u = this[o];
                    if (typeof u != "undefined") {
                        var a = this._getInsertIndex(u, o);
                        a !== o && (this._swapItems(o, a),
                        can.trigger(this, "length", [this.length]))
                    }
                }
                n.apply(this, arguments)
            },
            _getInsertIndex: function(e, t) {
                var n = this._getComparatorValue(e), r, i = 0;
                for (var s = 0; s < this.length; s++) {
                    r = this._getComparatorValue(this[s]);
                    if (typeof t != "undefined" && s === t) {
                        i = -1;
                        continue
                    }
                    if (this._comparator(n, r) < 0)
                        return s + i
                }
                return s + i
            },
            _getComparatorValue: function(e, t) {
                var n = typeof t == "string" ? t : this.comparator;
                return e && n && typeof n == "string" && (e = typeof e[n] == "function" ? e[n]() : e.attr(n)),
                e
            },
            _getComparatorValues: function() {
                var e = this
                  , t = [];
                return this.each(function(n, r) {
                    t.push(e._getComparatorValue(n))
                }
                ),
                t
            },
            sort: function(e, t) {
                var n, r, i, s, o = can.isFunction(e) ? e : this._comparator;
                for (var u, a, f = 0, l = this.length; f < l - 1; f++) {
                    a = f,
                    s = !0,
                    i = undefined;
                    for (u = f + 1; u < l; u++)
                        n = this._getComparatorValue(this.attr(u), e),
                        r = this._getComparatorValue(this.attr(a), e),
                        o.call(this, n, r) < 0 && (s = !1,
                        a = u),
                        i && o.call(this, n, i) < 0 && (s = !1),
                        i = n;
                    if (s)
                        break;
                    a !== f && this._swapItems(a, f, t)
                }
                return t || can.trigger(this, "length", [this.length]),
                this
            },
            _swapItems: function(e, t, n) {
                var r = this[e];
                [].splice.call(this, e, 1),
                [].splice.call(this, t, 0, r),
                n || can.trigger(this, "move", [r, t, e])
            }
        });
        var s = function(e) {
            return e[0] && can.isArray(e[0]) ? e[0] : can.makeArray(e)
        }
        ;
        return can.each({
            push: "length",
            unshift: 0
        }, function(e, t) {
            var n = can.List.prototype
              , r = n[t];
            n[t] = function() {
                if (this.comparator && arguments.length) {
                    var e = s(arguments)
                      , t = e.length;
                    while (t--) {
                        var n = can.bubble.set(this, t, this.__type(e[t], t))
                          , i = this._getInsertIndex(n);
                        Array.prototype.splice.apply(this, [i, 0, n]),
                        this._triggerChange("" + i, "add", [n], undefined)
                    }
                    return can.batch.trigger(this, "reset", [e]),
                    this
                }
                return r.apply(this, arguments)
            }
        }
        ),
        function() {
            var e = can.List.prototype
              , t = e.splice;
            e.splice = function(n, r) {
                var i = can.makeArray(arguments), s = [], o, u;
                if (!this.comparator)
                    return t.apply(this, i);
                for (o = 2,
                u = i.length; o < u; o++)
                    i[o] = this.__type(i[o], o),
                    s.push(i[o]);
                t.call(this, n, r),
                e.push.apply(this, s)
            }
        }
        (),
        can.Map
    })();

    $.ajaxPrefilter(function (options, originalOptions, xhr) {
        options['contentType'] = 'application/json';
        options['data'] = JSON.stringify(originalOptions['data']);
    });

    can.route.ready(false);
    new (can.Control(routes))(document);
    can.route.ready();
    console.log('TSM Application Started');
});
