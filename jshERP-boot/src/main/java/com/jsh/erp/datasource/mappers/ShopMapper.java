package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.Shop;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ShopMapper {
    int insertSelective(Shop record);

    int updateByPrimaryKeySelective(Shop record);

    Shop selectByPrimaryKey(Long id);

    List<Shop> selectByTenant(@Param("tenantId") Long tenantId, @Param("onlyEnabled") Boolean onlyEnabled);

    Shop selectByTenantAndName(@Param("tenantId") Long tenantId, @Param("name") String name);
}



