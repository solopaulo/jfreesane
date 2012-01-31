package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import au.com.southsky.jfreesane.SaneSession.SaneParameters;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

/**
 * Wraps an {@link InputStream} to provide some methods for deserializing SANE-related types.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneInputStream extends InputStream {
  private final SaneSession saneSession;
  private InputStream wrappedStream;

  public SaneInputStream(SaneSession saneSession, InputStream wrappedStream) {
    this.saneSession = saneSession;
    this.wrappedStream = wrappedStream;
  }

  @Override
  public int read() throws IOException {
    return wrappedStream.read();
  }

  public List<SaneDevice> readDeviceList() throws IOException {
    // Status first
    readWord().integerValue();

    // now we're reading an array, decode the length of the array (which
    // includes the null if the array is non-empty)
    int length = readWord().integerValue() - 1;

    if (length <= 0) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<SaneDevice> result = ImmutableList.builder();

    for (int i = 0; i < length; i++) {
      SaneDevice device = readSaneDevicePointer();
      if (device == null) {
        throw new IllegalStateException("null pointer encountered when not expected");
      }

      result.add(device);
    }

    // read past a trailing byte in the response that I haven't figured
    // out yet...
    readWord();

    return result.build();
  }

  /**
   * Reads a single {@link SaneDevice} definition pointed to by the pointer at the current location
   * in the stream. Returns {@code null} if the pointer is a null pointer.
   */
  private SaneDevice readSaneDevicePointer() throws IOException {
    if (!readPointer()) {
      // TODO(sjr): why is there always a null pointer here?
      // return null;
    }

    // now we assume that there's a sane device ready to parse
    return readSaneDevice();
  }

  /**
   * Reads a single pointer and returns {@code true} if it was non-null.
   */
  private boolean readPointer() throws IOException {
    return readWord().integerValue() != 0;
  }

  private SaneDevice readSaneDevice() throws IOException {
    String deviceName = readString();
    String deviceVendor = readString();
    String deviceModel = readString();
    String deviceType = readString();

    return new SaneDevice(this.saneSession, deviceName, deviceVendor, deviceModel, deviceType);
  }

  public String readString() throws IOException {
    // read the length
    int length = readWord().integerValue();

    if (length == 0) {
      return "";
    }

    // now read all the bytes
    byte[] input = new byte[length];
    if (read(input) != input.length) {
      throw new IllegalStateException("truncated input while reading string");
    }

    // skip the null terminator
    return new String(input, 0, input.length - 1, Charsets.ISO_8859_1);
  }

  public SaneParameters readSaneParameters() throws IOException {
    int frame = readWord().integerValue();
    boolean lastFrame = readWord().integerValue() == 1;
    int bytesPerLine = readWord().integerValue();
    int pixelsPerLine = readWord().integerValue();
    int lines = readWord().integerValue();
    int depth = readWord().integerValue();

    return new SaneSession.SaneParameters(frame, lastFrame, bytesPerLine, pixelsPerLine, lines,
        depth);
  }

  public SaneWord readWord() throws IOException {
    return SaneWord.fromStream(this);
  }
}