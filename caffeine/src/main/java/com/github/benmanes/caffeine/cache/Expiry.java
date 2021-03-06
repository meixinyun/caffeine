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

import javax.annotation.concurrent.ThreadSafe;

/**
 * Calculates when cache entries expire. A single expiration time is retained so that the lifetime
 * of an entry may be extended or reduced by subsequent evaluations.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@ThreadSafe
interface Expiry<K, V> {

  /**
   * Specifies that the entry should be automatically removed from the cache once the duration has
   * elapsed after the entry's creation. To indicate no expiration an entry may be given an
   * excessively long period, such as {@code Long#MAX_VALUE}.
   *
   * @param key the key represented by this entry
   * @param value the value represented by this entry
   * @param currentTime the current time, in nanoseconds
   * @return the length of time before the entry expires, in nanoseconds; may not be zero or
   *         negative.
   */
  long expireAfterCreate(K key, V value, long currentTime);

  /**
   * Specifies that the entry should be automatically removed from the cache once the duration has
   * elapsed after the replacement of its value. To indicate no expiration an entry may be given an
   * excessively long period, such as {@code Long#MAX_VALUE}. The {@code currentDuration} may be
   * returned to not modify the expiration time.
   *
   * @param key the key represented by this entry
   * @param value the value represented by this entry
   * @param currentTime the current time, in nanoseconds
   * @param currentDuration the current duration, in nanoseconds
   * @return the length of time before the entry expires, in nanoseconds; may not be zero or
   *         negative.
   */
  long expireAfterUpdate(K key, V value, long currentTime, long currentDuration);

  /**
   * Specifies that the entry should be automatically removed from the cache once the duration has
   * elapsed after its last read. To indicate no expiration an entry may be given an excessively
   * long period, such as {@code Long#MAX_VALUE}. The {@code currentDuration} may be returned to not
   * modify the expiration time.
   *
   * @param key the key represented by this entry
   * @param value the value represented by this entry
   * @param currentTime the current time, in nanoseconds
   * @param currentDuration the current duration, in nanoseconds
   * @return the length of time before the entry expires, in nanoseconds; may not be zero or
   *         negative.
   */
  long expireAfterRead(K key, V value, long currentTime, long currentDuration);
}
