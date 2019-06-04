package io.gemini.serialization.protostuff;


/**
 * protostuff 集合类序列化wrapper
 * @param <T>
 */
public class SerializeDeserializeWrapper<T> {

    private T data;

    public static <T> SerializeDeserializeWrapper<T> builder(T data) {
        SerializeDeserializeWrapper<T> wrapper = new SerializeDeserializeWrapper<>();
        wrapper.setData(data);
        return wrapper;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
