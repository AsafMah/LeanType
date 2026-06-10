// SPDX-License-Identifier: Apache-2.0
//
// Force-included (via -include) ONLY in the standalone host test build (CMakeLists.txt).
// The engine/test sources were written against AOSP's older clang, which pulled several standard
// headers transitively. Modern host g++ is stricter, so a few TUs reference CHAR_BIT / fixed-width
// ints / mem* without a direct include. This shim provides them globally for the host build
// without touching the shared sources (which compile fine under the NDK for the app).
#pragma once

#include <climits>   // CHAR_BIT, INT_MAX, ...
#include <cstdint>   // int32_t, uint8_t, ...
#include <cstddef>   // size_t, ptrdiff_t
#include <cstring>   // memcpy, memset, strlen
#include <cstdio>    // snprintf
