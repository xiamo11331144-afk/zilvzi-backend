package com.zilvzi.mapper;

import com.zilvzi.pojo.Partner;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 搭子关系Mapper接口
 */
public interface PartnerMapper {

    /**
     * 查询用户的搭子列表
     */
    List<Partner> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户收到的申请
     */
    List<Partner> selectByPartnerId(@Param("partnerId") Long partnerId);

    /**
     * 查询两人之间是否存在关系
     */
    Partner selectByUserAndPartner(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    List<Partner> selectAllByUserAndPartner(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    /**
     * 根据ID查询
     */
    Partner selectById(@Param("id") Long id);

    /**
     * 插入搭子关系
     */
    int insert(Partner partner);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updatePrevStatus(@Param("id") Long id, @Param("prevStatus") String prevStatus);

    int updateUserAndPartner(@Param("id") Long id, @Param("userId") Long userId, @Param("partnerId") Long partnerId);

    /**
     * 删除搭子关系
     */
    int deleteById(@Param("id") Long id);

    List<Partner> selectByUserAsPartner(@Param("partnerId") Long partnerId);

    List<Partner> selectBlockedByUserId(@Param("userId") Long userId);
}