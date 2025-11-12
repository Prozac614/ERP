package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.Shop;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.ShopMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ShopService {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private UserService userService;

    public List<Shop> findShopsByCurrentTenant(boolean onlyEnabled) throws Exception {
        User current = userService.getCurrentUser();
        Long tenantId = current.getTenantId();
        return shopMapper.selectByTenant(tenantId, onlyEnabled);
    }

    public void ensureDefaultShopsForTenant(Long tenantId) {
        // 不再自动创建默认店铺，由用户通过界面管理店铺
    }
}
