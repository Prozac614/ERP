package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialPriceHistory;
import com.jsh.erp.datasource.entities.MaterialPriceHistoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialPriceHistoryMapper {
    long countByExample(MaterialPriceHistoryExample example);

    int deleteByExample(MaterialPriceHistoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialPriceHistory record);

    int insertSelective(MaterialPriceHistory record);

    List<MaterialPriceHistory> selectByExample(MaterialPriceHistoryExample example);

    MaterialPriceHistory selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") MaterialPriceHistory record, @Param("example") MaterialPriceHistoryExample example);

    int updateByExample(@Param("record") MaterialPriceHistory record, @Param("example") MaterialPriceHistoryExample example);

    int updateByPrimaryKeySelective(MaterialPriceHistory record);

    int updateByPrimaryKey(MaterialPriceHistory record);
}

