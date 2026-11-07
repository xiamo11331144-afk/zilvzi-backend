package com.zilvzi.mapper;

import com.zilvzi.pojo.User;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 用户Mapper接口
 */
public interface UserMapper {

    /**
     * 根据ID查询用户
     */
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     */
    User selectByPhone(@Param("phone") String phone);

    /**
     * 根据邮箱查询用户
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 根据openid查询用户
     */
    User selectByOpenid(@Param("openid") String openid);

    /**
     * 插入用户
     */
    int insert(User user);

    /**
     * 更新用户
     */
    int update(User user);

    /**
     * 管理员专用：可更新 level/exp/role
     */
    int updateAdmin(User user);

    /**
     * 更新用户头像
     */
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    /**
     * 按经验值排序查询TOP用户
     */
    List<User> selectTopByExperience(@Param("limit") Integer limit);

    List<Map> selectRankingByCheckin(@Param("limit") Integer limit);

    /**
     * 按目标类型查询用户（排除自己）
     */
    List<User> selectByGoalType(@Param("goalType") String goalType, @Param("excludeUserId") Long excludeUserId);

    /**
     * 查询所有用户
     */
    List<User> selectAll();

    /**
     * 统计用户总数
     */
    Integer countAll();

    int deleteById(@Param("id") Long id);

    List<User> selectAllExcept(@Param("excludeUserId") Long excludeUserId);

    List<Map> selectGoalsByUserId(@Param("userId") Long userId);

    List<User> searchUsers(@Param("keyword") String keyword, @Param("excludeUserId") Long excludeUserId);

    /**
     * 专用更新经验值和等级（仅更新 level + experience，不影响 role 等字段）
     */
    int updateExperience(@Param("id") Long id, @Param("level") Integer level, @Param("experience") Integer experience);

    int updateMentorEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    Map<String, Object> selectMentorStats(@Param("userId") Long userId);

    /**
     * 排行榜查询（内联 streak 计算，避免 N+1 查询）
     */
    List<Map> selectRankingWithStreak(@Param("limit") Integer limit, @Param("today") String today);

    /**
     * 搭子匹配候选用户：按目标类型 + 可选省份筛选，联查打卡率
     */
    User selectByIdForUpdate(@Param("id") Long id);

    List<Map<String, Object>> selectMatchCandidates(@Param("excludeUserId") Long excludeUserId,
            @Param("goalTypes") List<String> goalTypes, @Param("province") String province,
            @Param("keyword") String keyword, @Param("requireMentor") Integer requireMentor,
            @Param("requesterRate") double requesterRate);
}