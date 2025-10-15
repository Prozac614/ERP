package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.DepotHeadMapper;
import com.jsh.erp.datasource.vo.BillMaterialSummary;
import com.jsh.erp.datasource.vo.ValidationDifference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    void validateQuantityConsistency_WithDifferentShops_ShouldValidateSeparately() {
        // 准备测试数据
        List<BillMaterialSummary> summaries = new ArrayList<>();

        // 商店A的数据
        BillMaterialSummary summary1 = createSummary(1L, "user1", "M001", "商品1", new BigDecimal("10"), "商店A");
        BillMaterialSummary summary2 = createSummary(2L, "user2", "M001", "商品1", new BigDecimal("10"), "商店A");

        // 商店B的数据（数量不同）
        BillMaterialSummary summary3 = createSummary(1L, "user1", "M001", "商品1", new BigDecimal("20"), "商店B");
        BillMaterialSummary summary4 = createSummary(2L, "user2", "M001", "商品1", new BigDecimal("30"), "商店B");

        // 未指定商店的数据
        BillMaterialSummary summary5 = createSummary(1L, "user1", "M001", "商品1", new BigDecimal("40"), null);
        BillMaterialSummary summary6 = createSummary(2L, "user2", "M001", "商品1", new BigDecimal("40"), null);

        summaries.add(summary1);
        summaries.add(summary2);
        summaries.add(summary3);
        summaries.add(summary4);
        summaries.add(summary5);
        summaries.add(summary6);

        // 调用私有方法进行测试
        List<ValidationDifference> differences = crossValidationService.validateQuantityConsistency(summaries);

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
    void validateQuantityConsistency_WithEmptyShop_ShouldValidateTogether() {
        // 准备测试数据
        List<BillMaterialSummary> summaries = new ArrayList<>();

        // 未指定商店的数据
        BillMaterialSummary summary1 = createSummary(1L, "user1", "M001", "商品1", new BigDecimal("10"), null);
        BillMaterialSummary summary2 = createSummary(2L, "user2", "M001", "商品1", new BigDecimal("20"), "");
        BillMaterialSummary summary3 = createSummary(3L, "user3", "M001", "商品1", new BigDecimal("10"), null);

        summaries.add(summary1);
        summaries.add(summary2);
        summaries.add(summary3);

        // 调用私有方法进行测试
        List<ValidationDifference> differences = crossValidationService.validateQuantityConsistency(summaries);

        // 验证结果
        assertNotNull(differences);
        assertEquals(1, differences.size());

        ValidationDifference difference = differences.get(0);
        assertEquals("M001", difference.getMaterialBarCode());
        assertEquals("商品1", difference.getMaterialName());
        assertEquals("未指定商店", difference.getShopName());
        assertEquals("QUANTITY_INCONSISTENT", difference.getDiffType());
    }

    private BillMaterialSummary createSummary(Long userId, String userName, String barCode, String materialName,
            BigDecimal quantity, String shopName) {
        BillMaterialSummary summary = new BillMaterialSummary();
        summary.setUserId(userId);
        summary.setUserName(userName);
        summary.setMaterialBarCode(barCode);
        summary.setMaterialName(materialName);
        summary.setTotalOutNumber(quantity);
        summary.setUnitPrice(new BigDecimal("100"));
        summary.setCreateTime(new Date());
        summary.setShopName(shopName);
        return summary;
    }
}
