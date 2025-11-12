package com.jsh.erp.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.erp.service.ShopService;
import com.jsh.erp.service.UserService;
import com.jsh.erp.datasource.entities.Shop;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/shop")
@Api(tags = { "店铺管理" })
public class ShopController {
    private Logger logger = LoggerFactory.getLogger(ShopController.class);

    @Resource
    private ShopService shopService;

    @Resource
    private UserService userService;

    @GetMapping(value = "/list")
    @ApiOperation(value = "获取当前租户的店铺列表")
    public BaseResponseInfo list(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Long tenantId = userService.getCurrentUser().getTenantId();
            List<Shop> shops = shopService.findShopsByCurrentTenant(true);
            Map<String, Object> data = new HashMap<>();
            data.put("rows", shops);
            res.code = 200;
            res.data = data;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }
}
