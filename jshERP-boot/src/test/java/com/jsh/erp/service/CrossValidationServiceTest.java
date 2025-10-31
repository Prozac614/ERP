package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.DepotHeadMapper;
import com.jsh.erp.datasource.vo.ValidationBillDetail;
import com.jsh.erp.datasource.vo.ValidationDifference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CrossValidationServiceTest {

    @InjectMocks
    private CrossValidationService crossValidationService;

    @Mock
    private DepotHeadMapper depotHeadMapper;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void buildDifferencesFromDetails_WithDifferentShops_ShouldValidateSeparately() throws Exception {
        // 准备测试数据
        List<ValidationBillDetail> details = new ArrayList<>();

        // 商店A的数据
        ValidationBillDetail detail1 = createDetail(1L, "user1", "M001", "商品1", new BigDecimal("10"), "商店A");
        ValidationBillDetail detail2 = createDetail(2L, "user2", "M001", "商品1", new BigDecimal("10"), "商店A");

        // 商店B的数据（数量不同）
        ValidationBillDetail detail3 = createDetail(1L, "user1", "M001", "商品1", new BigDecimal("20"), "商店B");
        ValidationBillDetail detail4 = createDetail(2L, "user2", "M001", "商品1", new BigDecimal("30"), "商店B");

        // 未指定商店的数据
        ValidationBillDetail detail5 = createDetail(1L, "user1", "M001", "商品1", new BigDecimal("40"), null);
        ValidationBillDetail detail6 = createDetail(2L, "user2", "M001", "商品1", new BigDecimal("40"), null);

        details.add(detail1);
        details.add(detail2);
        details.add(detail3);
        details.add(detail4);
        details.add(detail5);
        details.add(detail6);

        Method method = CrossValidationService.class.getDeclaredMethod("buildDifferencesFromDetails", List.class,
                List.class, Map.class);
        method.setAccessible(true);

        Map<Long, String> userMap = new HashMap<>();
        userMap.put(1L, "user1");
        userMap.put(2L, "user2");

        @SuppressWarnings("unchecked")
        List<ValidationDifference> differences = (List<ValidationDifference>) method.invoke(crossValidationService,
                details, Arrays.asList(1L, 2L), userMap);

        // 验证结果
        assertNotNull(differences);
        assertEquals(1, differences.size()); // 只有商店B的数据不一致

        ValidationDifference difference = differences.get(0);
        assertEquals("M001", difference.getMaterialBarCode());
        assertEquals("商品1", difference.getMaterialName());
        assertEquals("商店B", difference.getShopName());
        assertEquals("QUANTITY_INCONSISTENT", difference.getDiffType());
        assertTrue(difference.getDescription().contains("商店B"));
    }

    @Test
    void buildDifferencesFromDetails_WithEmptyShop_ShouldValidateTogether() throws Exception {
        // 准备测试数据
        List<ValidationBillDetail> details = new ArrayList<>();

        // 未指定商店的数据
        ValidationBillDetail detail1 = createDetail(1L, "user1", "M001", "商品1", new BigDecimal("10"), null);
        ValidationBillDetail detail2 = createDetail(2L, "user2", "M001", "商品1", new BigDecimal("20"), "");
        ValidationBillDetail detail3 = createDetail(3L, "user3", "M001", "商品1", new BigDecimal("10"), null);

        details.add(detail1);
        details.add(detail2);
        details.add(detail3);

        Method method = CrossValidationService.class.getDeclaredMethod("buildDifferencesFromDetails", List.class,
                List.class, Map.class);
        method.setAccessible(true);

        Map<Long, String> userMap = new HashMap<>();
        userMap.put(1L, "user1");
        userMap.put(2L, "user2");
        userMap.put(3L, "user3");

        @SuppressWarnings("unchecked")
        List<ValidationDifference> differences = (List<ValidationDifference>) method.invoke(crossValidationService,
                details, Arrays.asList(1L, 2L, 3L), userMap);

        // 验证结果
        assertNotNull(differences);
        assertEquals(1, differences.size());

        ValidationDifference difference = differences.get(0);
        assertEquals("M001", difference.getMaterialBarCode());
        assertEquals("商品1", difference.getMaterialName());
        assertEquals("未指定商店", difference.getShopName());
        assertEquals("QUANTITY_INCONSISTENT", difference.getDiffType());
    }

    private ValidationBillDetail createDetail(Long userId, String userName, String barCode, String materialName,
            BigDecimal quantity, String shopName) {
        ValidationBillDetail detail = new ValidationBillDetail();
        detail.setUserId(userId);
        detail.setUserName(userName);
        detail.setMaterialBarCode(barCode);
        detail.setMaterialName(materialName);
        detail.setQuantity(quantity);
        detail.setUnitPrice(new BigDecimal("100"));
        detail.setBillDate(new Date().toString());
        detail.setBillNumber("BN" + System.nanoTime());
        detail.setShopName(shopName);
        return detail;
    }
}
