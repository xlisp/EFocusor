#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string.h>

// One ordered reply at a time. Callers retain incoming commands until it has
// drained; no overwrite, truncation, heap growth, or waiting for TX space.
template <size_t Capacity, size_t Chunk = 8>
class CooperativeReply {
 public:
  bool busy() const { return offset_ < length_; }

  bool assign(const char* bytes, size_t length) {
    if (busy() || length > Capacity) return false;
    if (length) memcpy(bytes_, bytes, length);
    length_ = length;
    offset_ = 0;
    return true;
  }

  void reset() { offset_ = length_ = 0; }

  template <class Port>
  size_t drain(Port& port, size_t reserve = 0) {
    if (!busy()) return 0;
    const int available = port.availableForWrite();
    if (available <= 0 || static_cast<size_t>(available) <= reserve) return 0;
    size_t count = static_cast<size_t>(available) - reserve;
    if (count > Chunk) count = Chunk;
    if (count > length_ - offset_) count = length_ - offset_;
    const size_t written = port.write(bytes_ + offset_, count);
    offset_ += written;
    if (!busy()) reset();
    return written;
  }

 private:
  uint8_t bytes_[Capacity];
  size_t offset_ = 0;
  size_t length_ = 0;
};
