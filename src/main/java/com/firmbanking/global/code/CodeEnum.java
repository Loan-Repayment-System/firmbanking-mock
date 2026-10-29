package com.firmbanking.global.code;

/**
 * 코드성 테이블과 1:1인 enum. id는 그 테이블의 PK다.
 */
public interface CodeEnum {

    int getId();

    static <E extends Enum<E> & CodeEnum> E fromId(Class<E> type, int id) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getId() == id) {
                return constant;
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + "에 없는 id: " + id);
    }
}
