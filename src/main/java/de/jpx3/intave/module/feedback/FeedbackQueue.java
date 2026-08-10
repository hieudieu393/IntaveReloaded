/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.feedback;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FeedbackQueue {
  private static final int MAX_DIRECT_SIZE = 2048;
  private static final int DIRECT_TRANSLATION = 1024;
  private final FeedbackEntry[] directLocalAccess = new FeedbackEntry[MAX_DIRECT_SIZE];
  private final Map<Short, FeedbackEntry> fallbackLocalAccess = new HashMap<>();
  private FeedbackEntry head, tail;
  private int size;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public void add(FeedbackRequest<?> request) {
    FeedbackEntry entry = new FeedbackEntry(request);
    writeLock.lock();
    try {
      short userKey = request.userKey();
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        short localAccessKey = (short) (userKey + DIRECT_TRANSLATION);
        if (directLocalAccess[localAccessKey] != null) {
          directLocalAccess[localAccessKey].appendSameUserKey(entry);
        } else {
          directLocalAccess[localAccessKey] = entry;
        }
      } else if (fallbackLocalAccess.containsKey(userKey)) {
        fallbackLocalAccess.get(userKey).appendSameUserKey(entry);
      } else {
        fallbackLocalAccess.put(userKey, entry);
      }
      if (head == null) {
        head = tail = entry;
      } else {
        tail.setFollowingRequest(entry);
        tail = entry;
      }
      size++;
    } finally {
      writeLock.unlock();
    }
  }

  public FeedbackRequest<?> peek() {
    readLock.lock();
    try {
      return head == null ? null : head.request;
    } finally {
      readLock.unlock();
    }
  }

  public FeedbackRequest<?> peek(short userKey) {
    readLock.lock();
    try {
      FeedbackEntry entry;
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        entry = directLocalAccess[userKey + DIRECT_TRANSLATION];
      } else {
        entry = fallbackLocalAccess.get(userKey);
      }
      return entry == null ? null : entry.request;
    } finally {
      readLock.unlock();
    }
  }

  public FeedbackRequest<?> poll() {
    writeLock.lock();
    try {
      if (head == null) {
        return null;
      }
      FeedbackEntry entry = head;
      head = head.followingRequest();
      if (head == null) {
        tail = null;
      }
      short userKey = entry.request.userKey();
      FeedbackEntry replacement = entry.nextSameUserKeyEntry();
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        directLocalAccess[userKey + DIRECT_TRANSLATION] = replacement;
      } else if (replacement != null) {
        fallbackLocalAccess.put(userKey, replacement);
      } else {
        fallbackLocalAccess.remove(userKey);
      }
      if (replacement != null) {
        replacement.sameUserKeyTail = entry.sameUserKeyTail;
        entry.sameUserKeyNext = null;
      }
      size--;
      return entry.request;
    } finally {
      writeLock.unlock();
    }
  }

  // can be a bit expensive, shouldn't be used too often though
  public synchronized List<FeedbackRequest<?>> pollUpTo(long globalKey) {
    writeLock.lock();
    try {
      if (head == null) {
        return Collections.emptyList();
      }
      FeedbackEntry entry = head;
      List<FeedbackRequest<?>> list = null;
      while (entry != null && entry.globalIndex() < globalKey) {
        if (list == null) {
          list = new ArrayList<>();
        }
        list.add(entry.request);
        head = head.followingRequest();
        if (head == null) {
          tail = null;
        }
        FeedbackEntry replacement = entry.nextSameUserKeyEntry();
        short userKey = entry.request.userKey();
        if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
          directLocalAccess[userKey + DIRECT_TRANSLATION] = replacement;
        } else if (replacement != null) {
          fallbackLocalAccess.put(userKey, replacement);
        } else {
          fallbackLocalAccess.remove(userKey);
        }
        if (replacement != null) {
          replacement.sameUserKeyTail = entry.sameUserKeyTail;
          entry.sameUserKeyNext = null;
        }
        size--;
        entry = head;
        if (list.size() > 500 && list.size() % 100 == 0) {
          writeLock.unlock();
          writeLock.lock();
        }
      }
      return list == null ? Collections.emptyList() : list;
    } finally {
      writeLock.unlock();
    }
  }

  public synchronized boolean hasUserKey(short userKey) {
    readLock.lock();
    try {
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        return directLocalAccess[userKey + DIRECT_TRANSLATION] != null;
      } else {
        return fallbackLocalAccess.containsKey(userKey);
      }
    } finally {
      readLock.unlock();
    }
  }

  public synchronized int size() {
    readLock.lock();
    try {
      return size;
    } finally {
      readLock.unlock();
    }
  }

  public static class FeedbackEntry {
    private final FeedbackRequest<?> request;
    private FeedbackEntry nextRequest;
    private FeedbackEntry sameUserKeyNext;
    private FeedbackEntry sameUserKeyTail;

    public FeedbackEntry(FeedbackRequest<?> request) {
      this.request = request;
    }

    public FeedbackEntry followingRequest() {
      return nextRequest;
    }

    public void setFollowingRequest(FeedbackEntry nextRequest) {
      this.nextRequest = nextRequest;
    }

    public FeedbackEntry nextSameUserKeyEntry() {
      return sameUserKeyNext;
    }

    public void appendSameUserKey(FeedbackEntry entry) {
      if (sameUserKeyNext == null) {
        sameUserKeyNext = entry;
      } else {
        sameUserKeyTail.sameUserKeyNext = entry;
      }
      sameUserKeyTail = entry;
    }

    public long globalIndex() {
      return request.num();
    }

    @Override
    public String toString() {
      return "FeedbackEntry{" +
        "request=" + request +
        ", nextRequest=" + nextRequest +
        ", sameUserKeyNext=" + sameUserKeyNext +
        ", userKey=" + request.userKey() +
        '}';
    }
  }
}
