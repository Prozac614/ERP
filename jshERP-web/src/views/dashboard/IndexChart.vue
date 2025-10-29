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

              <a-col :md="12" :sm="24">
                <a-form-item label="数据维度" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <div style="display: flex; gap: 8px;">
                    <a-select
                      v-model="queryParam.dimensionType"
                      @change="onDimensionChange"
                      style="width: 120px"
                    >
                      <a-select-option value="daily">日</a-select-option>
                      <a-select-option value="monthly">月</a-select-option>
                      <a-select-option value="quarter">季度</a-select-option>
                      <a-select-option value="halfYear">半年</a-select-option>
                      <a-select-option value="year">年</a-select-option>
                    </a-select>
                    <template v-if="currentPickerType === 'date'">
                      <a-range-picker
                        v-model="queryParam.createTimeRange"
                        :format="currentPickerFormat"
                        :placeholder="currentPickerPlaceholder"
                        @change="onTimeRangeChange"
                        @ok="onTimeRangeOk"
                        style="width: 200px"
                        :key="'date-picker'"
                      />
                    </template>
                    <template v-else-if="currentPickerType === 'month'">
                      <a-range-picker
                        v-model="queryParam.createTimeRange"
                        :picker="'month'"
                        :format="currentPickerFormat"
                        :placeholder="currentPickerPlaceholder"
                        @change="onTimeRangeChange"
                        @ok="onTimeRangeOk"
                        style="width: 200px"
                        :key="'month-picker'"
                      />
                    </template>
                    <template v-else-if="currentPickerType === 'quarter'">
                      <a-range-picker
                        v-model="queryParam.createTimeRange"
                        :picker="'quarter'"
                        :format="currentPickerFormat"
                        :placeholder="currentPickerPlaceholder"
                        @change="onTimeRangeChange"
                        @ok="onTimeRangeOk"
                        style="width: 200px"
                        :key="'quarter-picker'"
                      />
                    </template>
                    <template v-else-if="currentPickerType === 'year'">
                      <a-range-picker
                        v-model="queryParam.createTimeRange"
                        :picker="'year'"
                        :format="currentPickerFormat"
                        :placeholder="currentPickerPlaceholder"
                        @change="onTimeRangeChange"
                        @ok="onTimeRangeOk"
                        style="width: 200px"
                        :key="'year-picker'"
                      />
                    </template>
                  </div>
                </a-form-item>
              </a-col>

              <a-col :md="6" :sm="24">
                <a-form-item label="库存状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select
                    placeholder="请选择库存状态"
                    v-model="queryParam.stockAlertStatus"
                    allowClear
                    @change="onStockAlertStatusChange"
                    style="width:100%"
                  >
                    <a-select-option v-for="item in stockAlertStatusOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <!-- 销售店铺筛选（仅影响首页销售统计相关接口时使用） -->
              <a-col :md="6" :sm="24">
                <a-form-item label="销售店铺" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select
                    mode="multiple"
                    placeholder="默认全部店铺"
                    v-model="queryParam.shopNames"
                    allowClear
                    :maxTagCount="3"
                    style="width:100%"
                  >
                    <a-select-option v-for="shop in shopList" :key="shop.id" :value="shop.name">{{ shop.name }}</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">{{ getQueryButtonText() }}</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                </a-col>
              </span>
            </a-row>

          </a-form>
        </div>
        <!-- 操作按钮区域 -->
        <div class="table-operator"  style="margin-top: 5px">
          <a-button @click="handleExport" type="primary" icon="download">导出库存</a-button>
          <a-button @click="calculateAllStockAlert"
                    type="default"
                    icon="calculator"
                    style="margin-left: 8px;"
                    :loading="calculatingAlert"
                    v-if="hasStockAlertPermission">
            库存预警校验
          </a-button>
          <a-button @click="refreshPeriodSummary"
                    type="default"
                    icon="sync"
                    style="margin-left: 8px;"
                    :loading="refreshingSummary">
            刷新汇总数据
          </a-button>


          <!-- 暂时隐藏展示所有数据按钮 -->
          <!-- 
          <a-button 
            :type="showAllProducts ? 'default' : 'primary'" 
            :icon="showAllProducts ? 'table' : 'unordered-list'" 
            @click="toggleShowAllProducts">
            {{ showAllProducts ? '分页显示' : '展示所有商品' }}
          </a-button>
          -->
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
                <a-row v-if="dateColumns.length > 0" style="padding-top: 10px;">
                  <a-col :span="24">
                    <span style="font-weight: 600; color: #666;">每日出库量列:</span>
                  </a-col>
                </a-row>
                <a-row v-if="dateColumns.length > 0" style="width: 500px; max-height: 120px; overflow-y: auto;">
                  <a-col v-for="(dateCol,index) in dateColumns" :key="dateCol.dataIndex" :span="6">
                    <a-checkbox :value="dateCol.dataIndex" disabled>
                      {{dateCol.title}}
                    </a-checkbox>
                  </a-col>
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
          <span class="total-amount" style="display:inline-block; margin-left:12px; font-weight:700; font-size:16px; color:#1890ff; white-space:nowrap;">
            库存总金额：{{ formatCurrency(totalStockValue) }}
          </span>
          <a-tooltip placement="left" title="商品库存期间统计表显示各商品的期间库存变动情况。
          支持按商品信息、分类、供应商等条件进行筛选。
          可以导出数据进行进一步分析。" slot="action">
            <a-icon type="question-circle" style="font-size:20px;float:right;" />
          </a-tooltip>
        </div>
        <!-- table区域-begin -->
        <div>
          <!-- 标准分页表格 -->
          <a-table
            ref="table"
            size="small"
            bordered
            rowKey="materialId"
            :columns="columns"
            :dataSource="dataSource"
            :components="handleDrag(columns)"
            :pagination="paginationConfig"
            :scroll="{ x: scroll.x, y: tableBodyHeight }"
            :loading="loading"
            :tableLayout="dynamicTableLayout"

            @change="handleTableChange">
            <template slot="action" slot-scope="text, record">
              <div>
                <a @click="viewChart(record)">查看图表</a>
                <template v-if="record.stockAlertStatus === 'STOCK_ALERT' && hasStockAlertPermission">
                  <a @click="ignoreStockRisk(record)" 
                     style="color: #fa8c16; display: block; margin-top: 4px;">
                    忽略风险
                  </a>
                </template>
                <template v-else-if="record.stockAlertStatus === 'RISK_IGNORED' && hasStockAlertPermission">
                  <a @click="focusStockRisk(record)" 
                     style="color: #1890ff; display: block; margin-top: 4px;">
                    关注风险
                  </a>
                </template>
              </div>
            </template>
            <template slot="customRenderStock" slot-scope="text, record">
              <span style="color:green" v-if="text > 0">{{text || 0}}</span>
              <span style="color:red" v-if="text < 0">{{text || 0}}</span>
              <span style="color:#666" v-if="text === 0 || text === null || text === undefined">0</span>
            </template>
            <template slot="dailyOutRender" slot-scope="text, record">
              <div style="text-align: center;">
                <span style="color: #1890ff; font-weight: 500" v-if="text > 0">{{text}}</span>
                <span style="color: #ccc" v-else>-</span>
              </div>
            </template>
            <!-- 库存状态列渲染模板 -->
            <template slot="stockAlertStatusRender" slot-scope="text, record">
              <a-tag v-if="text === 'NO_RISK'" color="green">
                <a-icon type="check-circle" /> 无风险
              </a-tag>
              <a-tag v-else-if="text === 'STOCK_ALERT'" color="red">
                <a-icon type="exclamation-circle" /> 库存告急
              </a-tag>
              <a-tag v-else-if="text === 'RISK_IGNORED'" color="orange">
                <a-icon type="eye-invisible" /> 忽略风险
              </a-tag>
              <a-tag v-else color="blue">
                <a-icon type="sync" spin /> 计算中
              </a-tag>
            </template>
          </a-table>
          
          <!-- 图表弹窗 -->
          <StockChartModal
            :visible="chartModal.visible"
            :materialInfo="chartModal.currentMaterial"
            :dateRange="queryParam.createTimeRange"
            :dimensionType="queryParam.dimensionType"
            @cancel="handleChartModalCancel"
          />
          




        </div>
      </a-card>
    </a-col>
  </a-row>
</template>

<script>
  import moment from 'moment'
import { getAction, postAction, downFile } from '@/api/manage'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import StockChartModal from '@/components/charts/StockChartModal'
  // import space removed

  export default {
    name: "IndexChart",
    components: {
      JEllipsis,
      StockChartModal
    },
            data () {
      return {
        // 查询条件
        queryParam: {
          materialParam: "",
          createTimeRange: [moment().subtract(1, 'months'), moment()],
          stockAlertStatus: "",
          dimensionType: "daily", // 新增维度类型
          shopNames: []
        },
        shopList: [],
        loadingRequest: null,
        debouncedLoadDataTimer: null,
        domCheckTimer: null,
        dailyOutData: {},
        dateColumns: [],
        calculatingAlert: false, // 库存预警校验加载状态
        refreshingSummary: false, // 刷新汇总数据加载状态
        overrideIgnoredStatus: false, // 是否覆盖忽略风险状态
        hasStockAlertPermission: false, // 库存预警权限标识
        // 库存总金额（两位小数）
        totalStockValue: null,
        
        // 库存状态选项
        stockAlertStatusOptions: [
          { value: "", label: "全部状态" },
          { value: "NO_RISK", label: "无风险" },
          { value: "STOCK_ALERT", label: "库存告急" },
          { value: "RISK_IGNORED", label: "忽略风险" }
        ],

        // 维度选项
        dimensionOptions: [
          { value: 'daily', label: '日' },
          { value: 'monthly', label: '月' },
          { value: 'quarter', label: '季度' },
          { value: 'halfYear', label: '半年' },
          { value: 'year', label: '年' }
        ],

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
        // 图表弹窗控制
        chartModal: {
          visible: false,
          currentMaterial: {}
        },

        // 表格滚动
        scroll: { x: undefined }, // 初始不设置滚动，让表格填满容器
        // 新增：表格内部滚动高度
        tableBodyHeight: 0,

        // 默认索引（包含库存状态列）
        defDataIndex: ['action', 'barCode', 'materialName', 'currentPeriodStock', 'lastSixMonthsSales', 'stockAlertStatus'],
        settingDataIndex: ['action', 'barCode', 'materialName', 'currentPeriodStock', 'lastSixMonthsSales', 'stockAlertStatus'],
        // 默认列（将根据横向滚动需求自适应设置fixed属性，优化宽度显示）
        defColumns: [
          {
            title: '操作',
            dataIndex: 'action',
            align: "center", 
            width: 100, // 🔧 80 → 100px，增加操作空间
            // fixed属性将在 applyAdaptiveFixedColumns() 中动态设置
            scopedSlots: { customRender: 'action' },
          },
          { title: '商品编码', dataIndex: 'barCode', width: 120 }, // 🔧 100 → 120px，增加编码显示空间
          { title: '商品名称', dataIndex: 'materialName', width: 180, ellipsis: true }, // 🔧 150 → 180px，增加名称显示空间
          { title: '当前库存', dataIndex: 'currentPeriodStock', width: 110, scopedSlots: { customRender: 'customRenderStock' } }, // 🔧 90 → 110px，增加库存数据显示空间
          { title: '近六月出库', dataIndex: 'lastSixMonthsSales', width: 120, align: 'center' },
          { title: '库存状态', dataIndex: 'stockAlertStatus', width: 130, align: 'center', scopedSlots: { customRender: 'stockAlertStatusRender' } } // 🔧 110 → 130px，增加状态显示空间
        ]

      }
    },
    computed: {
      columns() {
        // 🔧 过滤无效的settingDataIndex字段，确保只渲染有效列
        const validSettingDataIndex = this.settingDataIndex.filter(dataIndex => 
          this.defColumns.some(col => col.dataIndex === dataIndex)
        )
        
        const baseColumns = this.defColumns.filter(item => validSettingDataIndex.includes(item.dataIndex))
        
        // 🔧 确保所有列都有明确的宽度，防止过度拉伸
        const processedColumns = [...baseColumns, ...this.dateColumns].map(col => {
          if (!col.width) {
            console.warn(`⚠️ 列 ${col.title} 缺少width属性，设置默认宽度100px`)
            return { ...col, width: 100 }
          }
          return col
        })
        

        
        return processedColumns
      },
      allDataIndex() {
        return [...this.settingDataIndex, ...this.dateColumns.map(col => col.dataIndex)]
      },
      // 分页配置
      paginationConfig() {
        return this.ipagination
      },
      // 当前时间选择器类型
      currentPickerType() {
        const pickerMap = {
          'daily': 'date',
          'monthly': 'month',
          'quarter': 'quarter',
          'halfYear': 'quarter',
          'year': 'year'
        }
        return pickerMap[this.queryParam.dimensionType] || 'date'
      },
      
      // 当前时间选择器格式
      currentPickerFormat() {
        const formatMap = {
          'daily': 'YYYY-MM-DD',
          'monthly': 'YYYY-MM',
          'quarter': 'YYYY-[Q]Q',
          'halfYear': 'YYYY-[Q]Q',
          'year': 'YYYY'
        }
        return formatMap[this.queryParam.dimensionType] || 'YYYY-MM-DD'
      },
      
      // 当前时间选择器占位符
      currentPickerPlaceholder() {
        const placeholderMap = {
          'daily': ['开始日期', '结束日期'],
          'monthly': ['开始月份', '结束月份'],
          'quarter': ['开始季度', '结束季度'],
          'halfYear': ['开始半年', '结束半年'],
          'year': ['开始年份', '结束年份']
        }
        return placeholderMap[this.queryParam.dimensionType] || ['开始时间', '结束时间']
      },
      
      // 当前时间范围标签
      currentTimeLabel() {
        const labelMap = {
          'daily': '统计日期',
          'monthly': '统计月份',
          'quarter': '统计季度',
          'halfYear': '统计半年',
          'year': '统计年份'
        }
        return labelMap[this.queryParam.dimensionType] || '统计日期'
      },
      
      // 🔧 简化的表格布局策略
      dynamicTableLayout() {
        // 简单固定布局，避免复杂判断
        return 'fixed'
      }
    },
    watch: {
      'queryParam.shopNames': {
        handler(newVal) {
          console.log('店铺选择变化:', newVal)
          this.searchQuery()
        },
        deep: true
      }
    },
    created() {
      this.generateDateColumns()
      this.initShopList()
      this.loadStockData()
    },
    beforeDestroy() {
      // 清理内存
      if (this.loadingRequest) {
        this.loadingRequest.abort()
      }
      if (this.debouncedLoadDataTimer) {
        clearTimeout(this.debouncedLoadDataTimer)
      }
      if (this.domCheckTimer) {
        clearTimeout(this.domCheckTimer)
      }
      
      this.dataSource = []
      
      // 强制垃圾回收（如果可用）
      if (window.gc) {
        console.log('🗑️ 触发垃圾回收')
        window.gc()
      }
      // 移除resize监听
      if (this._resizeHandler) {
        window.removeEventListener('resize', this._resizeHandler);
      }
      

    },
    methods: {
      // 性能优化 - 日期范围验证
      validateDateRange(beginDate, endDate) {
        if (!beginDate || !endDate) return { valid: true }
        
        const daysDiff = endDate.diff(beginDate, 'days')
        if (daysDiff > 180) {
          this.$message.warning('为了保证性能，日期范围不能超过6个月')
          return { 
            valid: false, 
            adjustedRange: [endDate.clone().subtract(6, 'months'), endDate]
          }
        }
        if (daysDiff > 60) {
          this.$message.info('日期范围较大，可能影响加载速度')
        }
        return { valid: true }
      },
      // 加载库存总金额
      async loadTotalStockValue() {
        try {
          const res = await getAction('/depotItem/getTotalStockValue', {})
          if (res && res.code === 200 && res.data) {
            this.totalStockValue = res.data.totalStockValue != null ? res.data.totalStockValue : 0
          } else {
            this.totalStockValue = 0
          }
        } catch (e) {
          console.error('获取库存总金额失败:', e)
          this.totalStockValue = 0
        }
      },
      // 金额格式化（两位小数）
      formatCurrency(val) {
        const num = Number(val)
        if (!isFinite(num)) {
          return '-'
        }
        return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      },

      // 生成日期范围数组
      generateDateRange(beginDate, endDate) {
        const dates = []
        let current = beginDate.clone()
        while (current.isSameOrBefore(endDate)) {
          dates.push(current.format('YYYY-MM-DD'))
          current.add(1, 'day')
        }
        return dates
      },

      // 生成动态日期列
      generateDateColumns() {

        
        if (this.queryParam.dimensionType === 'quarter') {
          this.generateQuarterlyColumns()
        } else if (this.queryParam.dimensionType === 'halfYear') {
          this.generateHalfYearColumns()
        } else if (this.queryParam.dimensionType === 'year') {
          this.generateYearColumns()
        } else if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          const [beginDate, endDate] = this.queryParam.createTimeRange
          
          if (this.queryParam.dimensionType === 'daily') {
            this.generateDailyColumns(beginDate, endDate)
          } else if (this.queryParam.dimensionType === 'monthly') {
            this.generateMonthlyColumns(beginDate, endDate)
          }
        } else {
          this.dateColumns = []
        }
        

        
        // 🔧 智能滚动策略：根据实际宽度决定是否需要滚动
        const calculatedWidth = this.calculateTotalTableWidth()
        const containerWidth = this.getTableContainerWidth()
        
        if (calculatedWidth <= containerWidth) {
          // 计算宽度小于等于容器宽度时，不设置滚动，让表格填满容器
          this.scroll.x = undefined
        } else {
          // 计算宽度大于容器宽度时，设置滚动宽度
          this.scroll.x = calculatedWidth
        }
        


        // 🔧 关键：根据滚动需求自适应设置固定列
        this.applyAdaptiveFixedColumns()
      },

      // 生成日维度列
      generateDailyColumns(beginDate, endDate) {

        
        const validation = this.validateDateRange(beginDate, endDate)
        
        let actualBeginDate = beginDate
        let actualEndDate = endDate
        
        if (!validation.valid && validation.adjustedRange) {
          [actualBeginDate, actualEndDate] = validation.adjustedRange
          this.queryParam.createTimeRange = validation.adjustedRange
        }

        const dates = this.generateDateRange(actualBeginDate, actualEndDate)
        
        // 🔧 动态计算列宽：少列时填满剩余空间，多列时使用固定宽度
        const dynamicWidth = this.calculateDynamicColumnWidth(dates.length, 'daily')
        

        
        this.dateColumns = dates.map(date => ({
          title: this.generateColumnTitle(date, 'daily'),
          dataIndex: `out_${date}`,
          width: dynamicWidth,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        

      },

      // 生成月维度列
      generateMonthlyColumns(beginDate, endDate) {

        
        const months = []
        let current = beginDate.clone().startOf('month')
        
        while (current.isSameOrBefore(endDate, 'month')) {
          months.push(current.format('YYYY-MM'))
          current.add(1, 'month')
        }
        
        // 🔧 动态计算列宽：少列时填满剩余空间
        const dynamicWidth = this.calculateDynamicColumnWidth(months.length, 'monthly')
        

        
        this.dateColumns = months.map(month => ({
          title: this.generateColumnTitle(month, 'monthly'),
          dataIndex: `out_${month}`,
          width: dynamicWidth,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        

      },

      // 生成季维度列
      generateQuarterlyColumns() {

        
        const quarters = []
        const currentYear = moment().year()
        
        // 生成最近3年的季度
        for (let year = currentYear - 1; year <= currentYear; year++) {
          for (let quarter = 1; quarter <= 4; quarter++) {
            quarters.push(`${year}-Q${quarter}`)
          }
        }
        
        // 🔧 动态计算列宽：少列时填满剩余空间
        const dynamicWidth = this.calculateDynamicColumnWidth(quarters.length, 'quarter')
        

        
        this.dateColumns = quarters.map(quarter => ({
          title: this.generateColumnTitle(quarter, 'quarter'),
          dataIndex: `out_${quarter}`,
          width: dynamicWidth,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        

      },

      // 生成半年维度列
      generateHalfYearColumns() {

        
        const halfYears = []
        const currentYear = moment().year()
        
        // 生成最近3年的半年
        for (let year = currentYear - 1; year <= currentYear; year++) {
          halfYears.push(`${year}-H1`)
          halfYears.push(`${year}-H2`)
        }
        
        // 🔧 动态计算列宽：少列时填满剩余空间
        const dynamicWidth = this.calculateDynamicColumnWidth(halfYears.length, 'halfYear')
        

        
        this.dateColumns = halfYears.map(halfYear => ({
          title: this.generateColumnTitle(halfYear, 'halfYear'),
          dataIndex: `out_${halfYear}`,
          width: dynamicWidth,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        

      },

      // 生成年维度列
      generateYearColumns() {

        
        const years = []
        const currentYear = moment().year()
        
        // 生成最近5年的年份
        for (let year = currentYear - 2; year <= currentYear; year++) {
          years.push(`${year}`)
        }
        
        // 🔧 动态计算列宽：少列时填满剩余空间
        const dynamicWidth = this.calculateDynamicColumnWidth(years.length, 'year')
        

        
        this.dateColumns = years.map(year => ({
          title: this.generateColumnTitle(year, 'year'),
          dataIndex: `out_${year}`,
          width: dynamicWidth,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        

      },

      // 计算总表格宽度 - 简化版本，避免过度复杂化
      calculateTotalTableWidth() {
        // 🔧 使用过滤后的有效settingDataIndex计算固定列宽度
        const validSettingDataIndex = this.settingDataIndex.filter(dataIndex => 
          this.defColumns.some(col => col.dataIndex === dataIndex)
        )
        const baseColumns = this.defColumns.filter(item => validSettingDataIndex.includes(item.dataIndex))
        const fixedColumnsWidth = baseColumns.reduce((total, col) => total + (col.width || 0), 0)
        
        // 🔧 使用实际的动态列宽度计算
        let dynamicColumnsWidth = 0
        if (this.dateColumns.length > 0) {
          // 使用第一个动态列的实际宽度（因为所有动态列宽度相同）
          const actualColumnWidth = this.dateColumns[0].width || 80
          dynamicColumnsWidth = this.dateColumns.length * actualColumnWidth
        }
        
        // 简单的总宽度计算
        const totalWidth = fixedColumnsWidth + dynamicColumnsWidth
        const finalWidth = Math.max(totalWidth, 400) // 最小400px保证基本可读性
        

        
        return finalWidth
      },

      // 🔧 计算动态列宽：根据剩余空间智能分配
      calculateDynamicColumnWidth(dateColumnsCount, dimensionType) {
        // 获取表格容器的总宽度
        const containerWidth = this.getTableContainerWidth()
        
        // 计算固定列的总宽度
        const baseColumns = this.defColumns.filter(item => this.settingDataIndex.includes(item.dataIndex))
        const fixedColumnsWidth = baseColumns.reduce((total, col) => total + (col.width || 0), 0)
        
        // 计算可分配给动态列的剩余宽度
        const remainingWidth = containerWidth - fixedColumnsWidth - 40 // 预留40px边距
        
        // 设置每种维度的最小宽度和最大宽度
        const dimensionLimits = {
          'daily': { min: 60, max: 200, default: 80 },
          'monthly': { min: 80, max: 250, default: 120 },
          'quarter': { min: 100, max: 280, default: 150 },
          'halfYear': { min: 120, max: 300, default: 170 },
          'year': { min: 140, max: 320, default: 190 }
        }
        
        const limits = dimensionLimits[dimensionType] || dimensionLimits['daily']
        
        // 计算理想列宽：将剩余宽度平均分配给动态列
        let idealWidth = Math.floor(remainingWidth / dateColumnsCount)
        
        // 应用最小值和最大值限制
        let finalWidth = Math.max(limits.min, Math.min(idealWidth, limits.max))
        
        // 如果计算出的宽度小于默认值，使用默认值（通常发生在列数很多的情况下）
        if (finalWidth < limits.default && dateColumnsCount > 8) {
          finalWidth = limits.default
        }
        

        
        return finalWidth
      },

      // 获取表格容器宽度
      getTableContainerWidth() {
        try {
          // 尝试获取表格容器的实际宽度
          const tableWrapper = this.$el && this.$el.querySelector('.ant-table-wrapper')
          if (tableWrapper) {
            return tableWrapper.offsetWidth
          }
          
          // 退回到卡片容器宽度
          const cardBody = this.$el && this.$el.querySelector('.ant-card-body')
          if (cardBody) {
            return cardBody.offsetWidth - 48 // 减去padding
          }
          
          // 最终退回到窗口宽度的估算
          return Math.min(document.documentElement.clientWidth - 200, 1200)
        } catch (error) {
          console.warn('🔧 获取容器宽度失败，使用默认值:', error)
          return 1000 // 默认宽度
        }
      },

      // 🔧 核心功能：自适应固定列策略
      applyAdaptiveFixedColumns() {
        const needsHorizontalScroll = this.scroll.x !== undefined
        
                 
        
        if (needsHorizontalScroll) {
          // 需要横向滚动时，启用固定列（包含重要的基础信息列，优化宽度）
          this.defColumns = [
            {
              title: '操作',
              dataIndex: 'action',
              align: "center", 
              width: 100, // 🔧 优化宽度
              fixed: 'left', // 启用固定
              scopedSlots: { customRender: 'action' },
            },
            { title: '商品编码', dataIndex: 'barCode', width: 120, fixed: 'left' }, // 🔧 优化宽度
            { title: '商品名称', dataIndex: 'materialName', width: 180, ellipsis: true, fixed: 'left' }, // 🔧 优化宽度
            { title: '当前库存', dataIndex: 'currentPeriodStock', width: 110, fixed: 'left', scopedSlots: { customRender: 'customRenderStock' } }, // 🔧 优化宽度
            { title: '近六月出库', dataIndex: 'lastSixMonthsSales', width: 120, align: 'center', fixed: 'left' },
            { title: '库存状态', dataIndex: 'stockAlertStatus', width: 130, align: 'center', fixed: 'left', scopedSlots: { customRender: 'stockAlertStatusRender' } } // 🔧 优化宽度
          ]

        } else {
          // 不需要横向滚动时，禁用所有固定列（保持相同宽度）
          this.defColumns = [
            {
              title: '操作',
              dataIndex: 'action',
              align: "center", 
              width: 100, // 🔧 保持优化宽度
              // 不设置fixed属性
              scopedSlots: { customRender: 'action' },
            },
            { title: '商品编码', dataIndex: 'barCode', width: 120 }, // 🔧 保持优化宽度
            { title: '商品名称', dataIndex: 'materialName', width: 180, ellipsis: true }, // 🔧 保持优化宽度
            { title: '当前库存', dataIndex: 'currentPeriodStock', width: 110, scopedSlots: { customRender: 'customRenderStock' } }, // 🔧 保持优化宽度
            { title: '近六月出库', dataIndex: 'lastSixMonthsSales', width: 120, align: 'center' },
            { title: '库存状态', dataIndex: 'stockAlertStatus', width: 130, align: 'center', scopedSlots: { customRender: 'stockAlertStatusRender' } } // 🔧 保持优化宽度
          ]

        }
        
        // 强制更新组件以应用新的列配置
        this.$forceUpdate()
      },

      // 生成列标题
      generateColumnTitle(dateKey, dimensionType) {
        switch (dimensionType) {
          case 'daily':
            return moment(dateKey).format('MM-DD')
          case 'monthly':
            return moment(dateKey).format('YYYY年MM月')
          case 'quarter':
            return dateKey
          case 'halfYear':
            return dateKey
          case 'year':
            return dateKey
          default:
            return dateKey
        }
      },

      // 查询方法
      searchQuery() {
        this.ipagination.current = 1
        this.debouncedLoadStockData()
      },
              // 重置查询
        searchReset() {
          this.queryParam = {
            materialParam: "",
            createTimeRange: [moment().subtract(1, 'months'), moment()],
            stockAlertStatus: "",
            dimensionType: "daily", // 重置维度类型
            shopNames: [] // 重置店铺选择
          }
          this.setDefaultTimeRange()
          this.generateDateColumns()
          this.searchQuery()
        },

      // 防抖处理的数据加载
      debouncedLoadStockData() {
        if (this.debouncedLoadDataTimer) {
          clearTimeout(this.debouncedLoadDataTimer)
        }
        this.debouncedLoadDataTimer = setTimeout(() => {
          this.loadStockData()
        }, 500)
      },




      // 日期变化处理（兼容性保留）
      onDateChange(dates, dateStrings) {
        this.onTimeRangeChange(dates)
      },
      onDateOk(dates) {
        this.onTimeRangeOk(dates)
      },

      // 维度变化处理
      onDimensionChange(value) {
        this.queryParam.dimensionType = value
        
        this.setDefaultTimeRange()
        this.generateDateColumns()
        this.debouncedLoadStockData()
      },

      // 时间范围变化处理
      onTimeRangeChange(dates) {
        this.queryParam.createTimeRange = dates
        this.generateDateColumns()
        if (dates && dates.length === 2) {
          this.debouncedLoadStockData()
        }
      },
      
      onTimeRangeOk(dates) {
        console.log('选择的时间范围: ', dates)
      },

      // 获取维度标签
      getDimensionLabel(dimensionType) {
        const labelMap = {
          'daily': '日',
          'monthly': '月',
          'quarter': '季度',
          'halfYear': '半年',
          'year': '年'
        }
        return labelMap[dimensionType] || '未知'
      },

      // 获取查询按钮文本
      getQueryButtonText() {
        return '查询'
      },

      // 设置默认时间范围
      setDefaultTimeRange() {
        const now = moment()
        
        if (this.queryParam.dimensionType === 'daily') {
          this.queryParam.createTimeRange = [now.clone().subtract(1, 'month'), now]
        } else if (this.queryParam.dimensionType === 'monthly') {
          this.queryParam.createTimeRange = [now.clone().subtract(3, 'month'), now]
        } else if (this.queryParam.dimensionType === 'quarter') {
          this.queryParam.createTimeRange = [now.clone().subtract(1, 'year'), now]
        } else if (this.queryParam.dimensionType === 'halfYear') {
          this.queryParam.createTimeRange = [now.clone().subtract(1, 'year'), now]
        } else if (this.queryParam.dimensionType === 'year') {
          this.queryParam.createTimeRange = [now.clone().subtract(2, 'year'), now]
        }
      },

      // 库存状态变化处理
      onStockAlertStatusChange(value) {
        this.searchQuery()
      },

      // 加载库存数据（性能优化版本）
      loadStockData(page) {
        if (page) {
          this.ipagination.current = page
        }
        
        // 取消之前的请求
        if (this.loadingRequest) {
          this.loadingRequest.abort()
        }
        
        // 调试日志
        console.log('当前查询参数:', this.queryParam)
        console.log('店铺选择:', this.queryParam.shopNames)
        console.log('店铺列表:', this.shopList)
        
        this.loading = true
        const params = {
          currentPage: this.ipagination.current,
          pageSize: this.ipagination.pageSize,
          materialParam: this.queryParam.materialParam || '',
          stockAlertStatus: this.queryParam.stockAlertStatus || '',
          dimensionType: this.queryParam.dimensionType || 'daily',
          shopNames: this.queryParam.shopNames ? this.queryParam.shopNames.join(',') : ''
        }
        
        // 调试日志
        console.log('发送请求参数:', params)
        
        // 根据维度类型处理时间参数
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          // 所有维度都使用用户选择的时间范围
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
        } else {
          // 如果没有选择时间范围，获取最近几年的完整数据
          const now = moment()
          params.beginTime = now.clone().subtract(2, 'years').format('YYYY-MM-DD')
          params.endTime = now.format('YYYY-MM-DD')
          params.calculateAll = true
        }

        // 直接请求实时数据，无缓存机制
        this.loadingRequest = getAction('/depotItem/getMaterialStockWithDailyOutOptimized', params)
        this.loadingRequest.then((res) => {
          if (res.code === 200) {
            this.processDataResponse(res.data)
          } else {
            this.$message.error(res.data || '数据加载失败')
          }
        }).catch((error) => {
          if (error.name !== 'AbortError') {
            console.error('获取库存数据失败:', error)
            this.$message.error('数据加载失败')
          }
        }).finally(() => {
          this.loading = false
          this.loadingRequest = null
        })
      },
      // 初始化店铺列表（首页筛选）
      initShopList() {
        console.log('开始初始化店铺列表')
        getAction('/shop/list').then(res => {
          if (res && res.code === 200 && res.data && Array.isArray(res.data.rows)) {
            this.shopList = res.data.rows
            console.log('店铺列表初始化完成:', this.shopList)
            // 默认全选为空数组，由后端按"全部店铺"处理；如需显式传全选请改为 this.queryParam.shopNames = [...this.shopList]
          } else {
            console.warn('店铺列表初始化失败:', res)
          }
        }).catch(error => {
          console.error('店铺列表请求失败:', error)
        })
      },

      // 处理API响应数据
      processDataResponse(data) {
        this.dataSource = data.rows || []
        this.ipagination.total = data.total || 0
        this.dailyOutData = data.dailyOutData || {}
        
        // 更新权限标识
        this.hasStockAlertPermission = data.hasStockAlertPermission || false



        // 合并每日出库数据到商品数据中
        this.mergeDataOptimized()
        

      },

      // 高效的数据合并方法
      mergeDataOptimized() {
        if (!this.dailyOutData || Object.keys(this.dailyOutData).length === 0) {
          return
        }

        // 根据维度类型显示聚合提示
        if (this.queryParam.dimensionType !== 'daily') {
          const message = this.$message.loading('正在聚合数据，请稍候...', 0)
          
          // 延迟执行聚合，让提示先显示
          setTimeout(() => {
            this.performDataMerge()
            message()
          }, 100)
        } else {
          this.performDataMerge()
        }
      },

      // 执行数据合并
      performDataMerge() {
        // 使用requestAnimationFrame分批处理，避免UI阻塞
        const batchSize = 10
        let index = 0
        
        const processBatch = () => {
          const endIndex = Math.min(index + batchSize, this.dataSource.length)
          
          for (let i = index; i < endIndex; i++) {
            const item = this.dataSource[i]
            const dailyData = this.dailyOutData[item.barCode] || {}
            
            // 根据维度类型聚合数据
            if (this.queryParam.dimensionType === 'daily') {
              this.mergeDailyData(item, dailyData)
            } else if (this.queryParam.dimensionType === 'monthly') {
              this.mergeMonthlyData(item, dailyData)
            } else if (this.queryParam.dimensionType === 'quarter') {
              this.mergeQuarterlyData(item, dailyData)
            } else if (this.queryParam.dimensionType === 'halfYear') {
              this.mergeHalfYearData(item, dailyData)
            } else if (this.queryParam.dimensionType === 'year') {
              this.mergeYearData(item, dailyData)
            }
            
            // 后端已返回 lastSixMonthsSales（仅销售、滚动6个月），无需前端再聚合
          }
          
          index = endIndex
          if (index < this.dataSource.length) {
            requestAnimationFrame(processBatch)
          }
        }
        
        processBatch()
      },

      // 日维度数据合并（保持原有逻辑）
      mergeDailyData(item, dailyData) {
        this.dateColumns.forEach(column => {
          const date = column.dataIndex.replace('out_', '')
          this.$set(item, column.dataIndex, dailyData[date] || 0)
        })
      },

      // 月维度数据聚合
      mergeMonthlyData(item, dailyData) {
        this.dateColumns.forEach(column => {
          const monthKey = column.dataIndex.replace('out_', '')
          let monthlyTotal = 0
          
          // 计算该月份所有日期的出库量总和
          Object.keys(dailyData).forEach(date => {
            if (moment(date).format('YYYY-MM') === monthKey) {
              monthlyTotal += parseFloat(dailyData[date] || 0)
            }
          })
          
          this.$set(item, column.dataIndex, monthlyTotal)
        })
      },

      // 季维度数据聚合
      mergeQuarterlyData(item, dailyData) {
        this.dateColumns.forEach(column => {
          const quarterKey = column.dataIndex.replace('out_', '')
          let quarterlyTotal = 0
          
          // 计算该季度所有日期的出库量总和
          Object.keys(dailyData).forEach(date => {
            const dateMoment = moment(date)
            const year = dateMoment.year()
            const quarter = dateMoment.quarter()
            const currentQuarterKey = `${year}-Q${quarter}`
            
            if (currentQuarterKey === quarterKey) {
              quarterlyTotal += parseFloat(dailyData[date] || 0)
            }
          })
          
          this.$set(item, column.dataIndex, quarterlyTotal)
        })
      },

      // 半年维度数据聚合
      mergeHalfYearData(item, dailyData) {
        this.dateColumns.forEach(column => {
          const halfYearKey = column.dataIndex.replace('out_', '')
          let halfYearTotal = 0
          
          // 计算该半年所有日期的出库量总和
          Object.keys(dailyData).forEach(date => {
            const dateMoment = moment(date)
            const year = dateMoment.year()
            const quarter = dateMoment.quarter()
            const half = quarter <= 2 ? 'H1' : 'H2'
            const currentHalfYearKey = `${year}-${half}`
            
            if (currentHalfYearKey === halfYearKey) {
              halfYearTotal += parseFloat(dailyData[date] || 0)
            }
          })
          
          this.$set(item, column.dataIndex, halfYearTotal)
        })
      },

      // 年维度数据聚合
      mergeYearData(item, dailyData) {
        this.dateColumns.forEach(column => {
          const yearKey = column.dataIndex.replace('out_', '')
          let yearTotal = 0
          
          // 计算该年所有日期的出库量总和
          Object.keys(dailyData).forEach(date => {
            const dateMoment = moment(date)
            const year = dateMoment.year()
            const currentYearKey = `${year}`
            
            if (currentYearKey === yearKey) {
              yearTotal += parseFloat(dailyData[date] || 0)
            }
          })
          
          this.$set(item, column.dataIndex, yearTotal)
        })
      },

      // 计算近六个月出库量总和
      calculateSixMonthsOutTotal(dailyData) {
        if (!dailyData || Object.keys(dailyData).length === 0) {
          return 0
        }
        
        const now = moment()
        let total = 0
        
        // 预计算最近6个月的年月字符串
        const sixMonths = []
        for (let i = 0; i < 6; i++) {
          sixMonths.push(now.clone().subtract(i, 'month').format('YYYY-MM'))
        }
        
        // 高效计算：遍历日期数据，匹配月份
        Object.keys(dailyData).forEach(date => {
          const monthStr = moment(date).format('YYYY-MM')
          if (sixMonths.includes(monthStr)) {
            total += parseFloat(dailyData[date] || 0)
          }
        })
        
        return total
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
        // 🔧 强制过滤无效列，防止渲染错误
        const validCheckedValues = checkedValues.filter(dataIndex => 
          this.defColumns.some(col => col.dataIndex === dataIndex)
        )
        
        this.settingDataIndex = validCheckedValues
        
        // 验证列设置状态
        this.validateColumnSettings()
      },
      handleRestDefault() {
        this.settingDataIndex = [...this.defDataIndex]
        // 验证列设置状态
        this.validateColumnSettings()
      },
      // 验证列设置状态
      validateColumnSettings() {
        // 确保至少显示必要的列
        const requiredColumns = ['action', 'barCode', 'materialName']
        const missingColumns = requiredColumns.filter(col => !this.settingDataIndex.includes(col))
        
        if (missingColumns.length > 0) {
          this.$message.warning('已自动添加必要的显示列')
          this.settingDataIndex = [...new Set([...this.settingDataIndex, ...missingColumns])]
        }
      },

      // 拖拽支持
      handleDrag(columns) {
        return {}
      },
      // 操作方法
      viewChart(record) {
        this.showChartModal(record)
      },

      // 显示图表弹窗
      showChartModal(materialRecord) {
        console.log('显示图表弹窗，商品记录:', materialRecord)

        // 确保传递正确的数据结构给图表组件
        const materialInfo = {
          materialId: materialRecord.materialId,
          barCode: materialRecord.barCode,
          materialName: materialRecord.materialName,
          currentPeriodStock: materialRecord.currentPeriodStock,
          previousPeriodStock: materialRecord.previousPeriodStock,
          currentPeriodOut: materialRecord.currentPeriodOut,
          currentPeriodIn: materialRecord.currentPeriodIn,
          stockAlertStatus: materialRecord.stockAlertStatus
        }

        this.chartModal.chartType = 'history' // 默认显示库存历史
        this.chartModal.currentMaterial = materialInfo
        this.chartModal.visible = true
      },

      // 关闭图表弹窗
      handleChartModalCancel() {
        this.chartModal.visible = false
        this.chartModal.currentMaterial = {}
      },
      handleExport() {
        // 显示确认对话框
        this.$confirm({
          title: '确认导出',
          content: '确定要导出当前筛选条件下的所有库存数据吗？',
          okText: '确定',
          cancelText: '取消',
          onOk: () => {
            this.performExport()
          }
        })
      },
      
            performExport() {
        const loading = this.$message.loading('正在准备导出数据，请稍候...', 0)
        
        // 构造导出参数
        const params = {}
        
        // 添加商品筛选参数
        if (this.queryParam.materialParam) {
          params.materialParam = this.queryParam.materialParam
        }
        
        // 添加时间范围参数
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
        }
        
        // 使用downFile函数下载文件
        downFile('/depotItem/exportMaterialStock', params).then((data) => {
          loading()
          if (!data) {
            this.$message.warning('文件下载失败')
            return
          }
          
          // 创建下载链接
          if (typeof window.navigator.msSaveBlob !== 'undefined') {
            window.navigator.msSaveBlob(new Blob([data], {type: 'application/vnd.ms-excel'}), '商品库存数据.xls')
          } else {
            let url = window.URL.createObjectURL(new Blob([data], {type: 'application/vnd.ms-excel'}))
            let link = document.createElement('a')
            link.style.display = 'none'
            link.href = url
            link.setAttribute('download', '商品库存数据_' + this.getNowFormatStr() + '.xls')
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link) // 下载完成移除元素
            window.URL.revokeObjectURL(url) // 释放掉blob对象
          }
          
          this.$message.success('导出成功')
        }).catch((error) => {
          loading()
          console.error('导出失败:', error)
          this.$message.error('导出失败，请重试')
        })
      },
      
      // 获取当前时间格式化字符串
      getNowFormatStr() {
        return moment().format('YYYYMMDD_HHmmss')
      },
      
      showLowStockAlert() {
        this.$message.info('显示低库存预警')
      },

      // 库存风险操作方法
      ignoreStockRisk(record) {
        this.$confirm({
          title: '确认忽略风险',
          content: `确定要忽略商品"${record.materialName}"的库存风险吗？忽略后该商品将不再显示库存告急状态。`,
          okText: '确定',
          cancelText: '取消',
          onOk: () => {
            this.performIgnoreStockRisk(record)
          }
        })
      },

      focusStockRisk(record) {
        this.$confirm({
          title: '确认关注风险',
          content: `确定要重新关注商品"${record.materialName}"的库存风险吗？系统将重新计算该商品的库存告急状态。`,
          okText: '确定',
          cancelText: '取消',
          onOk: () => {
            this.performFocusStockRisk(record)
          }
        })
      },

      performIgnoreStockRisk(record) {
        const loading = this.$message.loading('正在忽略风险...', 0)

        postAction('/depotItem/ignoreStockRisk', { materialId: record.materialId }).then(res => {
          loading()
          if (res.code === 200) {
            this.$message.success('已忽略库存风险')
            // 更新本地数据
            record.stockAlertStatus = 'RISK_IGNORED'
            record.stockAlertIgnoredAt = new Date()
          } else {
            this.$message.error(res.data || '操作失败')
          }
        }).catch(error => {
          loading()
          console.error('忽略库存风险失败:', error)
          this.$message.error('操作失败，请重试')
        })
      },

      performFocusStockRisk(record) {
        const loading = this.$message.loading('正在重新关注风险...', 0)

        postAction('/depotItem/focusStockRisk', { materialId: record.materialId }).then(res => {
          loading()
          if (res.code === 200) {
            this.$message.success('已重新关注库存风险')
            // 刷新页面数据以获取最新状态
            this.loadStockData()
          } else {
            this.$message.error(res.data || '操作失败')
          }
        }).catch(error => {
          loading()
          console.error('关注库存风险失败:', error)
          this.$message.error('操作失败，请重试')
        })
      },

      // 批量计算库存预警状态
      async calculateAllStockAlert() {
        try {
          // 显示确认对话框
          this.$confirm({
            title: '库存预警校验配置',
            content: () => {
              return this.$createElement('div', [
                this.$createElement('p', { style: { marginBottom: '16px' } }, 
                  '此操作将重新校验所有商品的库存预警状态，请选择校验方式：'),
                this.$createElement('div', { style: { marginBottom: '12px' } }, [
                  this.$createElement('a-checkbox', {
                    props: { checked: this.overrideIgnoredStatus },
                    on: { change: (e) => { this.overrideIgnoredStatus = e.target.checked } }
                  }, '覆盖忽略风险状态')
                ]),
                this.$createElement('p', { 
                  style: { fontSize: '12px', color: '#666', margin: '0' } 
                }, '提示：取消勾选将保护已忽略风险的商品，勾选则重新校验所有商品（包括忽略风险的商品）')
              ])
            },
            okText: '确认校验',
            cancelText: '取消',
            onOk: async () => {
              this.calculatingAlert = true
              try {
                const requestData = {
                  preserveIgnoredStatus: !this.overrideIgnoredStatus
                }
                const res = await this.$http.post('/depotItem/calculateAllStockAlertStatus', requestData)
                if (res.code === 200) {
                  this.$message.success(res.data.message || '库存预警状态校验完成')
                  // 刷新数据
                  this.loadStockData()
                } else {
                  this.$message.error(res.data.message || res.data || '校验失败')
                }
              } catch (error) {
                console.error('批量校验失败:', error)
                this.$message.error('校验失败')
              } finally {
                this.calculatingAlert = false
              }
            }
          })
        } catch (error) {
          console.error('批量校验操作失败:', error)
          this.$message.error('操作失败')
        }
      },
      // 刷新汇总数据
      refreshPeriodSummary() {
        this.$confirm({
          title: '确认刷新汇总数据',
          content: '此操作将重新计算所有商品的期间汇总数据，包括本期结存、上期结存、本期出库入库、上期出库入库等。确定要继续吗？',
          okText: '确定',
          cancelText: '取消',
          onOk: () => {
            this.performRefreshPeriodSummary()
          }
        })
      },
      
      performRefreshPeriodSummary() {
        this.refreshingSummary = true
        
        postAction('/depotItem/refreshAllMaterialsPeriodSummary', {}).then(res => {
          if (res.code === 200) {
            this.$message.success('所有商品期间汇总数据刷新完成')
            // 重新加载页面数据
            this.loadStockData()
            // 刷新库存总金额
            this.loadTotalStockValue()
          } else {
            this.$message.error(res.data || '刷新失败')
          }
        }).catch(error => {
          console.error('刷新汇总数据失败:', error)
          this.$message.error('刷新失败，请重试')
        }).finally(() => {
          this.refreshingSummary = false
        })
      },
      

      
      // 新增：计算表格可用高度
      calcTableBodyHeight() {
        // 获取窗口高度
        const windowHeight = document.documentElement.clientHeight;
        // 获取搜索区高度
        const searchWrapper = this.$el.querySelector('.table-page-search-wrapper');
        const searchHeight = searchWrapper ? searchWrapper.offsetHeight : 0;
        // 获取操作按钮区高度
        const operator = this.$el.querySelector('.table-operator');
        const operatorHeight = operator ? operator.offsetHeight : 0;
        // 预估分页条高度（如有分页）
        const paginationHeight = 120; // 进一步增加分页高度预留
        // 预留边距/padding
        const padding = 80; // 进一步增加边距预留
        // 计算可用高度
        this.tableBodyHeight = windowHeight - searchHeight - operatorHeight - paginationHeight - padding;
        if (this.tableBodyHeight < 200) this.tableBodyHeight = 200; // 最小高度保护
      },
    },
    mounted() {
      // 初始化表格高度
      this.$nextTick(() => {
        this.calcTableBodyHeight();
        // 页面加载时拉取库存总金额
        this.loadTotalStockValue();
      });
      
      // 监听窗口resize
      this._resizeHandler = () => {
        this.calcTableBodyHeight();
      };
      window.addEventListener('resize', this._resizeHandler);
      
      // 基础调试方法
      window.debugIndexChartTable = () => {
        console.log('🔍 表格状态:')
        console.log(`  滚动: ${this.scroll.x ? '启用' : '禁用'}`)
        console.log(`  动态列: ${this.dateColumns.length}`)
        console.log(`  固定列: ${this.defColumns.filter(col => col.fixed).length}`)
      }
      
      console.log('💡 自适应固定列表格已启用')
    },
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

  /* 表格紧凑化样式 */
.ant-table-tbody > tr > td {
  padding: 8px 12px !important;
}

.ant-table-thead > tr > th {
  padding: 10px 12px !important;
}

/* 🔧 最终解决方案：slot参数修复 + 动态列智能填充 + 自适应固定列 + 智能滚动 */
.ant-table-wrapper {
  width: 100%;
  overflow-x: auto;
}

.ant-table-wrapper .ant-table table {
  table-layout: fixed !important;
  width: 100%;
}

/* 确保列头文字不换行，保持良好的显示效果 */
.ant-table-wrapper .ant-table-thead > tr > th {
  white-space: nowrap;
  text-align: center;
  vertical-align: middle;
  border-right: 1px solid #f0f0f0; /* 确保列边界清晰 */
}

.ant-table-wrapper .ant-table-tbody > tr > td {
  text-align: center;
  vertical-align: middle;
  border-right: 1px solid #f0f0f0; /* 确保列边界清晰 */
}

/* 🔧 修复无滚动条时列头和内容对齐问题 */
.ant-table-wrapper .ant-table table {
  table-layout: fixed !important;
  width: 100% !important;
}

/* 确保无滚动条时表格列的精确对齐 */
.ant-table-wrapper .ant-table-thead > tr > th,
.ant-table-wrapper .ant-table-tbody > tr > td {
  box-sizing: border-box;
  padding: 8px 12px !important;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 特别处理最后一列，取消右边框避免双重边框 */
.ant-table-wrapper .ant-table-thead > tr > th:last-child,
.ant-table-wrapper .ant-table-tbody > tr > td:last-child {
  border-right: none;
}


</style>