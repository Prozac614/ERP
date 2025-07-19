<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="6" :sm="24">
                <a-form-item label="商品信息" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入唛头、名称、助记码、规格、型号等信息" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>

              <a-col :md="6" :sm="24">
                <a-form-item label="统计日期" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-range-picker
                    style="width:100%"
                    v-model="queryParam.createTimeRange"
                    format="YYYY-MM-DD"
                    :placeholder="['开始时间', '结束时间']"
                    @change="onDateChange"
                    @ok="onDateOk"
                  />
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                </a-col>
              </span>
            </a-row>

          </a-form>
        </div>
        <!-- 操作按钮区域 -->
        <div class="table-operator"  style="margin-top: 5px">
          <a-button @click="handleExport" type="primary" icon="download">导出库存</a-button>
          <a-button icon="reload" @click="loadStockData(1)">刷新数据</a-button>
          <a-button icon="warning" @click="showLowStockAlert">低库存预警</a-button>
          <a-popover trigger="click" placement="right">
            <template slot="content">
              <a-checkbox-group @change="onColChange" v-model="settingDataIndex" :defaultValue="settingDataIndex">
                <a-row style="width: 500px">
                  <template v-for="(item,index) in defColumns">
                    <template>
                      <a-col :span="8">
                        <a-checkbox :value="item.dataIndex">
                          <j-ellipsis :value="item.title" :length="10"></j-ellipsis>
                        </a-checkbox>
                      </a-col>
                    </template>
                  </template>
                </a-row>
                <a-row style="padding-top: 10px;">
                  <a-col>
                    恢复默认列配置：<a-button @click="handleRestDefault" type="link" size="small">恢复默认</a-button>
                  </a-col>
                </a-row>
              </a-checkbox-group>
            </template>
            <a-button icon="setting">列设置</a-button>
          </a-popover>
          <a-tooltip placement="left" title="商品库存期间统计表显示各商品的期间库存变动情况。
          支持按商品信息、分类、供应商等条件进行筛选。
          可以导出数据进行进一步分析。" slot="action">
            <a-icon type="question-circle" style="font-size:20px;float:right;" />
          </a-tooltip>
        </div>
        <!-- table区域-begin -->
        <div>
          <a-table
            ref="table"
            size="middle"
            bordered
            rowKey="id"
            :columns="columns"
            :dataSource="dataSource"
            :components="handleDrag(columns)"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <a @click="viewStockDetail(record)">查看详情</a>
              <a-divider type="vertical" />
              <a @click="viewStockHistory(record)">库存历史</a>
              <a-divider type="vertical" />
              <a @click="adjustStock(record)">库存调整</a>
            </span>
            <template slot="customRenderStock" slot-scope="value, record">
              <span style="color:green" v-if="value > 0">{{value || 0}}</span>
              <span style="color:red" v-if="value < 0">{{value || 0}}</span>
              <span style="color:#666" v-if="value === 0 || value === null || value === undefined">0</span>
            </template>
          </a-table>
        </div>
      </a-card>
    </a-col>
  </a-row>
</template>

<script>
  import moment from 'moment'
  import { getAction } from '@/api/manage'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import Vue from 'vue'

  export default {
    name: "IndexChart",
    components: {
      JEllipsis
    },
    data () {
      return {
        // 查询条件
        queryParam: {
          materialParam: "",
          createTimeRange: [moment().subtract(1, 'months'), moment()]
        },
        // 页面样式
        cardStyle: 'padding: 0',
        loading: true,
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 表格数据
        dataSource: [],
        selectedRowKeys: [],
        // 分页
        ipagination: {
          current: 1,
          pageSize: 10,
          pageSizeOptions: ['10', '20', '30'],
          showTotal: (total, range) => {
            return range[0] + "-" + range[1] + " 共" + total + "条"
          },
          showQuickJumper: true,
          showSizeChanger: true,
          total: 0
        },
        // 表格滚动
        scroll: { x: 800 },
        // 默认索引
        defDataIndex: ['action', 'barCode', 'materialName', 'currentPeriodStock', 'previousPeriodStock', 'currentPeriodOut', 'previousPeriodOut'],
        settingDataIndex: ['action', 'barCode', 'materialName', 'currentPeriodStock', 'previousPeriodStock', 'currentPeriodOut', 'previousPeriodOut'],
        // 默认列
        defColumns: [
          {
            title: '操作',
            dataIndex: 'action',
            align: "center", 
            width: 150,
            scopedSlots: { customRender: 'action' },
          },
          { title: '商品编码', dataIndex: 'barCode', width: 120 },
          { title: '商品名称', dataIndex: 'materialName', width: 200, ellipsis: true },
          { title: '本期结存', dataIndex: 'currentPeriodStock', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '上期结存', dataIndex: 'previousPeriodStock', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '本期出库', dataIndex: 'currentPeriodOut', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '上期出库', dataIndex: 'previousPeriodOut', width: 120, scopedSlots: { customRender: 'customRenderStock' } }
        ],

      }
    },
    computed: {
      columns() {
        return this.defColumns.filter(item => this.settingDataIndex.includes(item.dataIndex))
      }
    },
    created() {
      this.loadStockData()
    },
    methods: {
      // 查询方法
      searchQuery() {
        this.ipagination.current = 1
        this.loadStockData()
      },
      // 重置查询
      searchReset() {
        this.queryParam = {
          materialParam: "",
          createTimeRange: [moment().subtract(1, 'months'), moment()]
        }
        this.searchQuery()
      },

      // 日期变化处理
      onDateChange(dates, dateStrings) {
        this.queryParam.createTimeRange = dates
      },
      onDateOk(dates) {
        console.log('选择的日期: ', dates)
      },
      // 加载库存数据
      loadStockData(page) {
        if (page) {
          this.ipagination.current = page
        }
        
        this.loading = true
        const params = {
          currentPage: this.ipagination.current,
          pageSize: this.ipagination.pageSize,
          materialParam: this.queryParam.materialParam || ''
        }
        
        // 如果有日期范围参数，添加到请求中
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
        }

        getAction('/depotItem/getMaterialPeriodStock', params).then((res) => {
          if (res.code === 200) {
            this.dataSource = res.data.rows || []
            this.ipagination.total = res.data.total || 0
          } else {
            this.$message.error(res.data || '数据加载失败')
          }
        }).catch((error) => {
          console.error('获取库存数据失败:', error)
          this.$message.error('数据加载失败')
        }).finally(() => {
          this.loading = false
        })
      },

              
      // 表格操作
      handleTableChange(pagination, filters, sorter) {
        this.ipagination = pagination
        this.loadStockData()
      },
      onSelectChange(selectedRowKeys) {
        this.selectedRowKeys = selectedRowKeys
      },
      // 列设置
      onColChange(checkedValues) {
        this.settingDataIndex = checkedValues
      },
      handleRestDefault() {
        this.settingDataIndex = [...this.defDataIndex]
      },
      // 拖拽支持
      handleDrag(columns) {
        return {}
      },
      // 操作方法
      viewStockDetail(record) {
        this.$message.info('查看商品库存详情：' + record.materialName)
      },
      viewStockHistory(record) {
        this.$message.info('查看库存历史：' + record.materialName)
      },
      adjustStock(record) {
        this.$message.info('库存调整：' + record.materialName)
      },
      handleExport() {
        this.$message.info('导出库存数据功能')
      },
      showLowStockAlert() {
        this.$message.info('显示低库存预警')
      }
    }
  }
</script>

<style lang="less" scoped>
  /* 查询表单样式 */
  .table-page-search-wrapper {
    margin-bottom: 24px;
    padding: 24px;
    background: #fafafa;
    border-radius: 6px;
    
    .table-page-search-submitButtons {
      .ant-btn {
        margin-right: 8px;
      }
    }
  }

  .table-operator {
    margin-bottom: 18px;
    .ant-btn {
      margin-right: 8px;
    }
  }

  .table-operator .ant-btn-group {
    margin-right: 8px;
  }
</style>