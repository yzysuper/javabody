package top.yzysuper.javabody;

import lombok.extern.log4j.Log4j2;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import top.yzysuper.javabody.dao.User;
import top.yzysuper.javabody.dao.UserMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;

@Log4j2
public class LocalCacheTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void init() {
        InputStream configStream = null;
        try {
            configStream = Resources.getResourceAsStream("mybatis-config.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }
        SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
        sqlSessionFactory = sqlSessionFactoryBuilder.build(configStream, "development");
        Configuration mybatisConfiguration = sqlSessionFactory.getConfiguration();
        System.out.printf("默认一级缓存级别：%s \n", mybatisConfiguration.getLocalCacheScope());
        System.out.printf("默认是否开启二级缓存：%s \n", mybatisConfiguration.isCacheEnabled());
        Collection<MappedStatement> mappedStatements = mybatisConfiguration.getMappedStatements();
        mappedStatements.stream().forEach(mappedStatement -> System.out.println(mappedStatement));
    }

    @Test
    public void testLocalCache1() {
        SqlSession sqlSession = sqlSessionFactory.openSession(true);
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        User user1 = userMapper.selectUserById("1");
        User user2 = userMapper.selectUserById("1");
        if (user1 != null) {
            log.debug(sqlSession.getConfiguration().getLocalCacheScope());
            log.info("默认一级缓存级别是SESSION，同一个sqlSession范围内有效！");
            Assert.assertTrue(user1.equals(user2));
        } else {
            Assert.assertFalse(true);
        }
    }

    @Test
    public void testLocalCache2() {
        SqlSession sqlSession = sqlSessionFactory.openSession(true);
        sqlSession.getConfiguration().setLocalCacheScope(LocalCacheScope.STATEMENT);
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        User user1 = userMapper.selectUserById("1");
        User user2 = userMapper.selectUserById("1");
        if (user1 != null) {
            log.debug(sqlSession.getConfiguration().getLocalCacheScope());
            log.info("缓存级别设定为STATEMENT时，同一个sqlSession内一级缓存无效！");
            Assert.assertFalse(user1.equals(user2));
            sqlSession.getConfiguration().setLocalCacheScope(LocalCacheScope.SESSION);
        } else {
            Assert.assertFalse(true);
        }
    }

    @Test
    public void testLocalCache3() {
        SqlSession sqlSession = sqlSessionFactory.openSession(true);
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        User user1 = userMapper.selectUserById("1");
        sqlSession.commit(); // sqlSession做提交操作和Connection的commit要区分开，autoCommit设定的是Connection的自动提交
        User user2 = userMapper.selectUserById("1");
        if (user1 != null) {
            log.debug(sqlSession.getConfiguration().getLocalCacheScope());
            log.info("默认一级缓存级别是SESSION，sqlSession提交会使一级缓存失效！");
            Assert.assertFalse(user1.equals(user2));
        } else {
            Assert.assertFalse(true);
        }
    }

    @Test
    public void testLocalCache4() {
        SqlSession sqlSession = sqlSessionFactory.openSession(true);
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        User user1 = userMapper.selectUserById("1");
        userMapper.updateUserById("1");
        User user2 = userMapper.selectUserById("1");
        if (user1 != null) {
            log.debug(sqlSession.getConfiguration().getLocalCacheScope());
            log.info("默认一级缓存级别是SESSION，更新操作会引发一级缓存的失效！");
            Assert.assertFalse(user1.equals(user2));
        } else {
            Assert.assertFalse(true);
        }
    }

    /**
     * 测试不同事务下localCache下不同事务中的数据状态
     * <p>
     * 没有使用二级缓存
     * </p>
     * <ul>
     * <li>设定隔离级别为2（读已提交）时，如果采用SESSION级别的localCache就会发生<b>同一事务中二次查询无法read committed。</b></li>
     * <li>设定隔离级别为4（可重复读，默认值），设定localCache基本无影响，维持了可重复读的定义。</li>
     * </ul>
     * <p>
     * 网上都说localCache会引发<b>脏读</b>其实是一种错误的认知，认为没有读到最新的修改就是脏读了。
     * 脏读定义：
     * “A dirty read (aka uncommitted dependency) occurs when a transaction is allowed to read data from a row that
     * has been modified by another running transaction and not yet committed.”
     * </p>
     * <p>
     * 结论：
     * 要不要使用一级缓存重要的考虑点是“read committed”。抛开业务场景，如果隔离级别为可重复读，普通的读就是可重复读，
     * 这时设定为SESSION级别还是有减少重复语句对数据库造成压力的作用的。
     * </p>
     * <p>
     * 参考：
     * https://github.com/mybatis/mybatis-3/issues/191
     * https://tech.meituan.com/2018/01/19/mybatis-cache.html
     *
     * @author yzysuper
     */
    @Test
    public void testLocalCache5() {
        SqlSession sqlSession1 = sqlSessionFactory.openSession(false); // 语句在事务中执行
        SqlSession sqlSession2 = sqlSessionFactory.openSession(false); // 语句在事务中执行
        try {
            sqlSession1.getConnection().setTransactionIsolation(2);
            log.info("当前隔离级别为[{}]", sqlSession1.getConnection().getTransactionIsolation());
        } catch (SQLException e) {
            e.printStackTrace();
        }
//        sqlSession1.getConfiguration().setLocalCacheScope(LocalCacheScope.STATEMENT);
        UserMapper userMapper1 = sqlSession1.getMapper(UserMapper.class);
        UserMapper userMapper2 = sqlSession2.getMapper(UserMapper.class);
        User user1 = userMapper1.selectUserById("1"); // 普通的读取
        userMapper2.updateUserById("1");
        sqlSession2.commit(); // 当前隔离级别为读已提交，sqlSession1的第二条读语句应该要读到已提交的数据
        User user2 = userMapper1.selectUserById("1");
        User user3 = userMapper2.selectUserById("1");
        sqlSession1.commit(); // 排除提交引起的缓存失效
        if (user1 != null) {
            log.debug(sqlSession1.getConfiguration().getLocalCacheScope());
            log.debug(sqlSession2.getConfiguration().getLocalCacheScope());
            log.info("默认一级缓存级别是SESSION，其它sqlSession下做更新操作会引起延迟读取数据的问题！");
            System.out.println(user1);
            System.out.println(user2);
            System.out.println(user3);
            Assert.assertTrue(user1.equals(user2)); // 没有达到读已提交的效果，将LocalCacheScope的级别设定为STATEMENT可以读到最新
            Assert.assertFalse(user1.equals(user3));
        } else {
            Assert.assertFalse(true);
        }
    }
}
