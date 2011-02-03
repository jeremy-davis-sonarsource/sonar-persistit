/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit.unit;

import java.util.TreeMap;

import org.junit.Test;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.KeyFilter;
import com.persistit.KeyState;
import com.persistit.Persistit;
import com.persistit.exception.PersistitException;

/**
 * @version 1.0
 */
public class KeyFilterTest1 extends PersistitUnitTestCase {

    @Test
    public void test1() {
        System.out.print("test1 ");
        final Key key = new Key(_persistit);
        key.append("atlantic");
        key.append((float) 1.3);
        KeyFilter kf = new KeyFilter(key);
        String s = kf.toString();
        assertEquals("{\"atlantic\",(float)1.3}", s);

        kf = kf.append(KeyFilter.rangeTerm("x", "z"));

        key.append("y");
        assertTrue(kf.selected(key));
        key.to("w");
        assertTrue(!kf.selected(key));
        key.to("x");
        assertTrue(kf.selected(key));
        key.to("xx");
        assertTrue(kf.selected(key));
        key.to("yzzz");
        assertTrue(kf.selected(key));
        key.to("z");
        assertTrue(kf.selected(key));
        key.to("z0");
        assertTrue(!kf.selected(key));

        kf = kf.append(
                KeyFilter.orTerm(new KeyFilter.Term[] {
                        KeyFilter.rangeTerm(new Integer(100), new Integer(150)),
                        KeyFilter.rangeTerm(new Integer(200), new Integer(250)),
                        KeyFilter.rangeTerm(new Integer(300), new Integer(350),
                                true, false, null), })).limit(2, 5);

        s = kf.toString();
        String t = "{\"atlantic\",>(float)1.3,\"x\":\"z\",{100:150,200:250,[300:350)},*<}";
        assertEquals(t, s);

        key.to("x");

        key.append(125);
        assertTrue(kf.selected(key));
        key.to(175);
        assertTrue(!kf.selected(key));

        key.to(200);
        assertTrue(kf.selected(key));
        key.append("tom");
        assertTrue(kf.selected(key));
        key.append("dick");
        assertTrue(!kf.selected(key));
        key.append("harry");
        assertTrue(!kf.selected(key));
        final KeyFilter kf2 = kf.limit(2, 7);
        assertTrue(kf2.selected(key));

        s = kf2.toString();
        t = "{\"atlantic\",>(float)1.3,\"x\":\"z\",{100:150,200:250,[300:350)},*,*,*<}";
        assertEquals(t, s);

        key.cut(3);

        key.to(249);
        assertTrue(kf.selected(key));
        key.to(250);
        assertTrue(kf.selected(key));
        key.to(251);
        assertTrue(!kf.selected(key));
        key.to(299);
        assertTrue(!kf.selected(key));
        key.to(300);
        assertTrue(kf.selected(key));
        key.to(350);
        assertTrue(!kf.selected(key));

        System.out.println("- done");
    }

    @Test
    public void test2() {
        System.out.print("test2 ");
        final Key key = new Key(_persistit);
        key.append("atlantic");
        key.append((float) 1.3);
        KeyFilter kf = new KeyFilter(key);
        final String s = kf.toString();
        assertEquals("{\"atlantic\",(float)1.3}", s);
        kf = kf.append(KeyFilter.rangeTerm("x", "z", true, false));
        key.append("a");
        assertTrue(kf.traverse(key, true));
        assertEquals("{\"atlantic\",(float)1.3,\"x\"}", key.toString());
        key.to("zz");
        assertTrue(kf.traverse(key, false));
        assertEquals("{\"atlantic\",(float)1.3,\"z\"}-", key.toString());
        System.out.println("- done");
    }

    @Test
    public void test3() throws PersistitException {
        System.out.print("test3 ");
        final Exchange ex = _persistit.getExchange("persistit", "KeyFilter1",
                true);
        final Key key = ex.getKey();
        ex.removeAll();
        for (int i = 0; i < 100; i++) {
            ex.getValue().put("Value " + i);
            ex.to(i).store();
        }
        final KeyFilter.Term orTerm = KeyFilter.orTerm(new KeyFilter.Term[] {
                KeyFilter.rangeTerm(new Integer(10), new Integer(20), true,
                        false, null),
                KeyFilter.rangeTerm(new Integer(50), new Integer(60), true,
                        false, null),
                KeyFilter.rangeTerm(new Integer(80), new Integer(90), false,
                        true, null), });
        final KeyFilter kf = new KeyFilter().append(orTerm);

        ex.to(Key.BEFORE);
        boolean[] traversed = new boolean[100];
        Key.Direction direction = Key.GT;
        while (ex.traverse(direction, true)) {
            if (kf.selected(ex.getKey())) {
                final int k = key.reset().decodeInt();
                traversed[k] = true;
                direction = Key.GT;
            } else {
                if (!kf.traverse(key, true)) {
                    break;
                }
                direction = Key.GTEQ;
            }
        }
        for (int k = 0; k < 100; k++) {
            final boolean expected = ((k >= 10) && (k < 20))
                    || ((k >= 50) && (k < 60)) || ((k > 80) && (k <= 90));
            assertEquals(expected, traversed[k]);
        }

        ex.to(Key.AFTER);
        traversed = new boolean[100];
        direction = Key.LT;
        while (ex.traverse(direction, true)) {
            if (kf.selected(ex.getKey())) {
                final int k = key.reset().decodeInt();
                traversed[k] = true;
                direction = Key.LT;
            } else {
                if (!kf.traverse(key, false)) {
                    break;
                }
                direction = Key.LTEQ;
            }
        }
        for (int k = 0; k < 100; k++) {
            final boolean expected = ((k >= 10) && (k < 20))
                    || ((k >= 50) && (k < 60)) || ((k > 80) && (k <= 90));
            assertEquals(expected, traversed[k]);
        }
        System.out.println("- done");
    }

    @Test
    public void test4() throws PersistitException {
        System.out.print("test4 ");
        final TreeMap treeMap = new TreeMap();
        final Exchange ex = _persistit.getExchange("persistit", "KeyFilter1",
                true);
        ex.removeAll();

        final Key key = ex.getKey();
        key.clear().append("atlantic");
        key.append((float) 1.3);
        final KeyFilter kf = new KeyFilter(key)
                .append(new KeyFilter.Term[] {
                        KeyFilter.rangeTerm("x", "z"),
                        KeyFilter.orTerm(new KeyFilter.Term[] {
                                KeyFilter.rangeTerm(new Integer(100),
                                        new Integer(150), true, false, null),
                                KeyFilter.rangeTerm(new Integer(200),
                                        new Integer(250), true, false, null),
                                KeyFilter.rangeTerm(new Integer(300),
                                        new Integer(350), true, false, null), }) })
                .limit(2, 5);

        final String s = kf.toString();
        final String t = "{\"atlantic\",<(float)1.3,[\"x\":\"z\"),{[100:150),[200:250),[300:350)},*<}";

        key.append("x");
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.append(125);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(175);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(200);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.append("tom");
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.append("dick");
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.append("harry");
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.cut(3);

        key.to(249);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(250);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(299);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(300);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        key.to(350);
        if (kf.selected(key)) {
            treeMap.put(new KeyState(key), key.toString());
        }
        ex.getValue().put(key.toString());
        ex.store();

        TreeMap treeMapCopy = new TreeMap(treeMap);
        key.clear().append(Key.BEFORE);
        Key.Direction direction = Key.GT;
        while (ex.traverse(direction, true)) {
            final KeyState ks = new KeyState(key);
            if (kf.selected(key)) {
                assertEquals(key.toString(), treeMapCopy.get(ks));
                treeMapCopy.remove(ks);
                direction = Key.GT;
            } else {
                assertEquals(null, treeMap.get(ks));
                if (!kf.traverse(key, true)) {
                    break;
                }
                direction = Key.GTEQ;
            }
        }
        assertEquals(0, treeMapCopy.size());

        treeMapCopy = new TreeMap(treeMap);
        key.clear().append(Key.AFTER);
        direction = key.LT;
        while (ex.traverse(direction, true)) {
            final KeyState ks = new KeyState(key);
            if (kf.selected(key)) {
                assertEquals(key.toString(), treeMapCopy.get(ks));
                treeMapCopy.remove(ks);
                direction = key.LT;
            } else {
                assertEquals(null, treeMap.get(ks));
                if (!kf.traverse(key, false)) {
                    break;
                }
                direction = key.LTEQ;
            }
        }
        assertEquals(0, treeMapCopy.size());
        System.out.println("- done");
    }

    @Test
    public void test5() throws PersistitException {
        System.out.print("test5 ");
        KeyFilter filter;
        filter = new KeyFilter("{:1}");
        filter = new KeyFilter("{ :1 }");
        filter = new KeyFilter("{1:}");
        filter = new KeyFilter("{ 1: }");
        filter = new KeyFilter("{\"id\", (long) 100:  }");
        filter = new KeyFilter("{\"id\", : (long) 200 }");
        System.out.println("- done");
    }

    @Test
    public void test6() throws PersistitException {
        System.out.print("test6 ");
        final Exchange ex = _persistit.getExchange("persistit", "KeyFilter1",
                true);
        ex.removeAll();
        final KeyFilter filter = new KeyFilter(
                "{2,*,4,(long)1004:(long)1007,3,>*<}");
        ex.append(2).append(1L).append(4).append(1006L).append(3)
                .append(1005007L);
        ex.getValue().put("test6");
        ex.store();
        ex.clear().append(Key.BEFORE);
        assertTrue(ex.traverse(Key.GTEQ, filter, 0));
        System.out.println("- done");
    }

    @Test
    public void test7() throws PersistitException {
        final KeyFilter filter = new KeyFilter("{\"byName\",\"foo\",>*<}");
        final Key key = new Key((Persistit) null);
        boolean result;

        key.clear().append(Key.BEFORE);
        result = filter.traverse(key, true);
        assertTrue(result);
        key.clear().append(Key.BEFORE);
        result = filter.traverse(key, false);
        assertFalse(result);

        key.clear().append(Key.AFTER);
        result = filter.traverse(key, false);
        assertTrue(result);
        key.clear().append(Key.AFTER);
        result = filter.traverse(key, true);
        assertFalse(result);

    }

    @Test
    public void test8() throws PersistitException {
        final KeyFilter filter = new KeyFilter("{1,*<}");
        final Key key = new Key((Persistit) null);
        final Key key2 = new Key((Persistit) null);
        key.append(Key.AFTER);
        assertFalse(filter.selected(key));
        assertTrue(filter.traverse(key, false));
        key2.append(1).append(1);
        assertTrue(filter.selected(key));
        assertTrue(key.compareTo(key2) > 0);
        // assertFalse(filter.traverse(key, false));
    }

    @Test
    public void test9() throws PersistitException {
        final KeyFilter filter = new KeyFilter("{1:2}");
        final Key key = new Key((Persistit) null);
        final Key key2 = new Key((Persistit) null);
        key.append(Key.AFTER);
        assertFalse(filter.selected(key));
        assertTrue(filter.traverse(key, false));
        key2.append(2).append(Key.AFTER);
        assertTrue(filter.selected(key));
        assertTrue(key.compareTo(key2) == 0);
        // assertFalse(filter.traverse(key, false));
    }

    public static void main(final String[] args) throws Exception {
        new KeyFilterTest1().initAndRunTest();
    }

    public void runAllTests() throws Exception {
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
    }

}