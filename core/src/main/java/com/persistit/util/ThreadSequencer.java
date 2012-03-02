/**
 * Copyright (C) 2012 Akiban Technologies Inc.
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

package com.persistit.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * <p>
 * Utility allows tests to define execution sequences to confirm specific
 * concurrent execution patterns. Application code incorporates calls to the
 * static method {@link #sequence(long)}. Each usage in the application code
 * should follow this pattern:
 * </p>
 * <ul>
 * <li>In the {@link SequencerConstants} interface, add a static variable with a
 * name denoting the place in code where sequencing will occur as follows:
 * 
 * <pre>
 * <code>
 *   final static int SOME_LOCATION = ThreadSequence.allocate("SOME_LOCATION");
 * </code>
 * </pre>
 * 
 * </li>
 * <li>In the class being tested, add a static import to ThreadSequence.* and
 * then call itself:
 * 
 * <pre>
 * <code>
 *      sequence(SOME_LOCATION);
 * </code>
 * </pre>
 * 
 * </li>
 * </ul>
 * <p>
 * The {@link #allocate()} method simply allocates a unique integer. Currently
 * the maximum number of allocated locations is 64.
 * </p>
 * <p>
 * The {@link #sequence(int)} method blocks on a Semaphore until a condition for
 * release is recognized. The condition is determined by a schedule that the
 * test class must define.
 * </p>
 * <p>
 * The entire ThreadSequence mechanism must be enabled via
 * {@link #enableSequencer(boolean))}. By default the calls to {@link #sequence(int)}
 * invoke an empty method in the NullSequencer subclass, which is fast.
 * </p>
 * <p>
 * Note: a missing or malformed schedule will cause threads blocked within the
 * sequence method to remain blocked forever.
 * </p>
 * 
 * @author peter
 * 
 */
public class ThreadSequencer implements SequencerConstants {

    private final static DisabledSequencer DISABLED_SEQUENCER = new DisabledSequencer();

    private final static EnabledSequencer ENABLED_SEQUENCER = new EnabledSequencer();

    private static volatile Sequencer _sequencer = DISABLED_SEQUENCER;

    private final static List<String> LOCATIONS = new ArrayList<String>();

    public synchronized static int allocate(final String locationName) {
        for (final String alreadyRegistered : LOCATIONS) {
            assert !alreadyRegistered.equals(locationName) : "Location name " + locationName + " is already in use";
        }
        int value = LOCATIONS.size();
        assert value < 64 : "Too many ThreadSequence locations";
        LOCATIONS.add(locationName);
        return value;
    }

    public static void sequence(final int location) {
        _sequencer.sequence(location);
    }

    public static void enableSequencer(final boolean history) {
        ENABLED_SEQUENCER.clear();
        if (history) {
            ENABLED_SEQUENCER.enableHistory();
        }
        _sequencer = ENABLED_SEQUENCER;
    }

    public static void disableSequencer() {
        _sequencer = DISABLED_SEQUENCER;
        ENABLED_SEQUENCER.clear();
    }

    public static void addSchedule(final int[] awaitLocations, final int[] releaseLocations) {
        ENABLED_SEQUENCER.addSchedule(bits(awaitLocations), bits(releaseLocations));
    }

    public static String sequencerHistory() {
        return ENABLED_SEQUENCER.history();
    }

    public static void addSchedules(final int[][] pairs) {
        for (int index = 0; index < pairs.length; index += 2) {
            addSchedule(pairs[index], pairs[index + 1]);
        }
    }

    public static int[] array(int... args) {
        return args;
    }

    private static long bits(final int[] locations) {
        long bits = 0;
        for (final int location : locations) {
            assert location >= 0 && location < 64 : "Location must be between 0 and 63, inclusive";
            bits |= (1L << location);
        }

        return bits;
    }

    interface Sequencer {
        /**
         * A location is a long with one bit set that denotes one of 64 possible
         * locations in code where a join point can occur.
         * 
         * @param location
         */
        public void sequence(final int location);

        /**
         * Clear the schedule
         */
        public void clear();

        /**
         * Add an element to the schedule. The arguments each represent a set of
         * locations in code. A schedule element affects the behavior of the
         * {@link #sequence(long)} method. When a thread calls
         * sequence(location), that thread blocks until the set of all currently
         * blocked threads covers the await field of one of the schedule
         * elements. Once the await field is covered, then the threads waiting
         * at locations denoted by the release field of that schedule element
         * are allowed to continue.
         * 
         * @param from
         *            bit map of locations
         * @param to
         */
        public void addSchedule(final long await, final long release);
    }

    private static class DisabledSequencer implements Sequencer {

        @Override
        public void sequence(int location) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void addSchedule(long await, long release) {
        }
    };

    private static class EnabledSequencer implements Sequencer {
        private final List<Long> _schedule = new ArrayList<Long>();
        private final Semaphore[] _semaphores = new Semaphore[64];
        private long _waiting = 0;
        private long _enabled = 0;
        private int[] _waitingCount = new int[64];
        private List<Integer> _history;

        {
            for (int index = 0; index < _semaphores.length; index++) {
                _semaphores[index] = new Semaphore(0);
            }
        }

        @Override
        public void sequence(int location) {
            assert location >= 0 && location < 64 : "Location must be between 0 and 63, inclusive";
            Semaphore semaphore = null;

            synchronized (this) {
                if ((_enabled & (1L << location)) == 0) {
                    return;
                }
                
                _waiting |= (1L << location);
                _waitingCount[location]++;
                semaphore = _semaphores[location];
                long release = 0;
                for (int index = 0; index < _schedule.size(); index += 2) {
                    long await = _schedule.get(index);
                    if ((_waiting & await) == await) {
                        release = _schedule.get(index + 1);
                        break;
                    }
                }
                for (int index = 0; index < 64; index++) {
                    if ((release & (1L << index)) != 0) {
                        if (location == index) {
                            semaphore = null;
                        } else {
                            _semaphores[index].release();
                        }
                    }
                }
                if (_history != null) {
                    _history.add(location);
                }
            }

            if (semaphore != null) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            synchronized (this) {
                if (_history != null) {
                    _history.add(Integer.MAX_VALUE - location);
                }
                if (--_waitingCount[location] == 0) {
                    _waiting &= ~(1L << location);
                }
            }
        }

        @Override
        public synchronized void clear() {
            _schedule.clear();
            _waiting = 0;
            _history = null;
            for (int index = 0; index < _semaphores.length; index++) {
                _semaphores[index].release(Integer.MAX_VALUE);
                _semaphores[index] = new Semaphore(0);
            }
        }

        @Override
        public synchronized void addSchedule(long await, long release) {
            for (int index = 0; index < _schedule.size(); index += 2) {
                long current = _schedule.get(index);
                assert (current & await) != current : "Schedules may not overlap";
                assert (current & await) != await : "Schedules may not overlap";
                assert (await & release) != 0 : "No thread is released";
            }
            _schedule.add(await);
            _schedule.add(release);
            _enabled |= release;
        }

        private void enableHistory() {
            _history = new ArrayList<Integer>();
        }

        public synchronized String history() {
            StringBuilder sb = new StringBuilder();
            if (_history != null) {
                for (Integer location : _history) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    int l = location;
                    if (l < 64) {
                        sb.append('+');
                        sb.append(LOCATIONS.get(l));
                    } else if ((l = Integer.MAX_VALUE - l) < 64) {
                        sb.append('-');
                        sb.append(LOCATIONS.get(l));
                    }
                }
            }
            _history.clear();
            return sb.toString();
        }

    }
}
