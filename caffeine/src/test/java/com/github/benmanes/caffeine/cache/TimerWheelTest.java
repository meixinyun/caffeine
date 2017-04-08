/*
 * Copyright 2017 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(singleThreaded = true)
public final class TimerWheelTest {
  TimerWheel<Integer, Integer> timerWheel;
  @Mock Predicate<Node<Integer, Integer>> evictor;
  @Captor ArgumentCaptor<Node<Integer, Integer>> captor;

  @BeforeMethod
  public void beforeMethod() {
    MockitoAnnotations.initMocks(this);
    timerWheel = new TimerWheel<>(evictor);
  }

  @Test(dataProvider = "schedule")
  public void schedule(long nanos, int expired) {
    when(evictor.test(captor.capture())).thenReturn(true);

    for (int timeout : new int[] { 25, 90, 240 }) {
      timerWheel.schedule(new Timer(TimeUnit.SECONDS.toNanos(timeout)));
    }
    timerWheel.advance(nanos);
    verify(evictor, times(expired)).test(any());

    for (Node<?, ?> node : captor.getAllValues()) {
      assertThat(node.getAccessTime(), is(lessThan(nanos)));
    }
  }

  @DataProvider(name = "schedule")
  public Object[][] providesSchedule() {
    return new Object[][] {
      { TimeUnit.SECONDS.toNanos(10), 0 },
      { TimeUnit.MINUTES.toNanos(3),  2 },
      { TimeUnit.MINUTES.toNanos(10), 3 }
    };
  }

  @Test(dataProvider = "fuzzySchedule")
  public void schedule_fuzzy(long nanos, long[] times) {
    when(evictor.test(captor.capture())).thenReturn(true);

    int expired = 0;
    for (long timeout : times) {
      if (timeout <= nanos) {
        expired++;
      }
      timerWheel.schedule(new Timer(timeout));
    }
    timerWheel.advance(nanos);
    verify(evictor, times(expired)).test(any());

    for (Node<?, ?> node : captor.getAllValues()) {
      assertThat(node.getAccessTime(), is(lessThan(nanos)));
    }
    checkTimerWheel(nanos, times);
  }

  @DataProvider(name = "fuzzySchedule")
  public Object[][] providesFuzzySchedule() {
    long[] times = new long[5_000];
    long bound = TimeUnit.DAYS.toNanos(5);
    for (int i = 0; i < times.length; i++) {
      times[i] = ThreadLocalRandom.current().nextLong(bound);
    }
    long nanos = ThreadLocalRandom.current().nextLong(bound);
    return new Object[][] {{ nanos, times }};
  }

  private void checkTimerWheel(long nanos, long[] times) {
    for (int i = 0; i < timerWheel.wheel.length; i++) {
      for (int j = 0; j < timerWheel.wheel[i].length; j++) {
        for (long timer : getTimers(timerWheel.wheel[i][j])) {
          String msg = String.format("wheel[%s][%d] by %ss", i, j,
              TimeUnit.NANOSECONDS.toSeconds(nanos - timer));
          assertThat(msg, timer, is(greaterThan(nanos)));
        }
      }
    }
  }

  private Multiset<Long> getTimers(Node<?, ?> setinel) {
    Multiset<Long> timers = HashMultiset.create();
    for (Node<?, ?> node = setinel.getNextInAccessOrder();
        node != setinel; node = node.getNextInAccessOrder()) {
      timers.add(node.getAccessTime());
    }
    return timers;
  }

  @Test(dataProvider = "cascade")
  public void cascade(long nanos, long timeout, int span) {
    timerWheel.schedule(new Timer(timeout));
    timerWheel.advance(nanos);

    int count = 0;
    for (int i = 0; i < span; i++) {
      for (int j = 0; j < timerWheel.wheel[i].length; j++) {
        count += getTimers(timerWheel.wheel[i][j]).size();
      }
    }
    assertThat("\n" + timerWheel.toString(), count, is(1));
  }

  @DataProvider(name = "cascade")
  public Iterator<Object[]> providesCascade() {
    List<Object[]> args = new ArrayList<>();
    for (int i = 1; i < TimerWheel.SPANS.length - 1; i++) {
      long duration = TimerWheel.SPANS[i];
      long timeout = ThreadLocalRandom.current().nextLong(duration + 1, 2 * duration);
      long nanos = ThreadLocalRandom.current().nextLong(duration + 1, timeout - 1);
      args.add(new Object[] { nanos, timeout, i});
    }
    return args.iterator();
  }

  private static final class Timer implements Node<Integer, Integer> {
    Node<Integer, Integer> prev;
    Node<Integer, Integer> next;
    long accessTime;

    Timer(long accessTime) {
      setAccessTime(accessTime);
    }

    @Override public long getAccessTime() {
      return accessTime;
    }
    @Override public void setAccessTime(long accessTime) {
      this.accessTime = accessTime;
    }
    @Override public Node<Integer, Integer> getPreviousInAccessOrder() {
      return prev;
    }
    @Override public void setPreviousInAccessOrder(@Nullable Node<Integer, Integer> prev) {
      this.prev = prev;
    }
    @Override public Node<Integer, Integer> getNextInAccessOrder() {
      return next;
    }
    @Override public void setNextInAccessOrder(@Nullable Node<Integer, Integer> next) {
      this.next = next;
    }

    @Override public Integer getKey() { return null; }
    @Override public Object getKeyReference() { return null; }
    @Override public Integer getValue() { return null; }
    @Override public Object getValueReference() { return null; }
    @Override public void setValue(Integer value, ReferenceQueue<Integer> referenceQueue) {}
    @Override public boolean containsValue(Object value) { return false; }
    @Override public boolean isAlive() { return false; }
    @Override public boolean isRetired() { return false; }
    @Override public boolean isDead() { return false; }
    @Override public void retire() {}
    @Override public void die() {}
  }
}
