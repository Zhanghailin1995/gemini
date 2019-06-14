package io.gemini.transport.payload;

import io.gemini.serialization.io.InputBuf;
import io.gemini.serialization.io.OutputBuf;

/**
 * gemini
 * io.gemini.transport.payload.PayloadHolder
 *
 * @author zhanghailin
 */
public class PayloadHolder {

    private byte serializerCode;

    private byte[] bytes;
    private InputBuf inputBuf;
    private OutputBuf outputBuf;


    public byte serializerCode() {
        return serializerCode;
    }

    public byte[] bytes() {
        return bytes;
    }

    public void bytes(byte serializerCode, byte[] bytes) {
        this.serializerCode = serializerCode;
        this.bytes = bytes;
    }

    public InputBuf inputBuf() {
        return inputBuf;
    }

    public void inputBuf(byte serializerCode, InputBuf inputBuf) {
        this.serializerCode = serializerCode;
        this.inputBuf = inputBuf;
    }

    public OutputBuf outputBuf() {
        return outputBuf;
    }

    public void outputBuf(byte serializerCode, OutputBuf outputBuf) {
        this.serializerCode = serializerCode;
        this.outputBuf = outputBuf;
    }

    // help gc
    public void clear() {
        bytes = null;
        inputBuf = null;
        outputBuf = null;
    }

    public int size() {
        return (bytes == null ? 0 : bytes.length)
                + (inputBuf == null ? 0 : inputBuf.size())
                + (outputBuf == null ? 0 : outputBuf.size());
    }
}
