package top.yzysuper.javabody.dao;

public interface UserMapper {
    User readLockUserById(String id);

    User writeLockUserById(String id);

    User selectUserById(String id);

    int updateUserById(String id);
}
