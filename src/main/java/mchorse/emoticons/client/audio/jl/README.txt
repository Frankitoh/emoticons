JLayer (MP3 decoder)
====================

The classes in this package are the MP3 decoder of JLayer, copied from
https://github.com/umjammer/jlayer (tag 1.0.2), which is based on JLayer
1.0.1 by JavaZOOM (http://www.javazoom.net/javalayer/javalayer.html).

Only the "decoder" package was copied. Changes made to it:
  - The "package" line was moved here so it doesn't clash with other mods
    that ship their own copy of JLayer.
  - SynthesisFilter.load_d() returns the table from SfdTable.java (the same
    512 values that JLayer keeps in its "sfd.ser" resource) instead of
    reading the resource from inside the jar.

JLayer is licensed under the GNU Library General Public License, version 2
or (at your option) any later version. A copy of the LGPL 2.1 is in
LICENSE-JLayer-LGPL-2.1.txt at the root of this repository.
