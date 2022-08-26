package server.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NumberWrapper<T extends Number> {
    @JsonProperty("value")
    T value;

    public T getValue() {
        return value;
    }

    public NumberWrapper(){}

    public NumberWrapper(T val){
        this();
        value = val;
    }

    public void setValue(T val) {
        this.value = val;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
