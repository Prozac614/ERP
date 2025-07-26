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
          <a-button @click="calculateAllStockAlert"
                    type="default"
                    icon="calculator"
                    style="margin-left: 8px;"
                    :loading="calculatingAlert"
                    v-if="hasStockAlertPermission">
            库存预警校验
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
                  <template v-for="(dateCol,index) in dateColumns">
                    <a-col :span="6" :key="dateCol.dataIndex">
                      <a-checkbox :value="dateCol.dataIndex" disabled>
                        {{dateCol.title}}
                      </a-checkbox>
                    </a-col>
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
          <!-- 标准分页表格 -->
          <a-table
            ref="table"
            size="small"
            bordered
            rowKey="id"
            :columns="columns"
            :dataSource="dataSource"
            :components="handleDrag(columns)"
            :pagination="paginationConfig"
            :scroll="{ x: scroll.x, y: tableBodyHeight }"
            :loading="loading"
            :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <div>
                <a @click="viewChart(record)">查看图表</a>
              </div>
              <div v-if="record.stockAlertStatus === 'STOCK_ALERT' && hasStockAlertPermission" style="margin-top: 4px;">
                <a @click="ignoreStockRisk(record)"
                   style="color: #fa8c16;">
                  忽略风险
                </a>
              </div>
              <div v-if="record.stockAlertStatus === 'RISK_IGNORED' && hasStockAlertPermission" style="margin-top: 4px;">
                <a @click="focusStockRisk(record)"
                   style="color: #1890ff;">
                  关注风险
                </a>
              </div>
            </span>
            <template slot="customRenderStock" slot-scope="value, record">
              <span style="color:green" v-if="value > 0">{{value || 0}}</span>
              <span style="color:red" v-if="value < 0">{{value || 0}}</span>
              <span style="color:#666" v-if="value === 0 || value === null || value === undefined">0</span>
            </template>
            <template slot="dailyOutRender" slot-scope="value, record, index, column">
              <span style="color: #1890ff; font-weight: 500" v-if="value > 0">{{value}}</span>
              <span style="color: #ccc" v-else>-</span>
            </template>
            <!-- 隐藏库存状态列渲染模板 -->
            <template slot="stockAlertStatusRender" slot-scope="value, record">
              <a-tag v-if="value === 'NO_RISK'" color="green">
                <a-icon type="check-circle" /> 无风险
              </a-tag>
              <a-tag v-else-if="value === 'STOCK_ALERT'" color="red">
                <a-icon type="exclamation-circle" /> 库存告急
              </a-tag>
              <a-tag v-else-if="value === 'RISK_IGNORED'" color="orange">
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
          stockAlertStatus: ""
        },
        loadingRequest: null,
        dailyOutData: {},
        dateColumns: [],
        calculatingAlert: false, // 库存预警校验加载状态
        overrideIgnoredStatus: false, // 是否覆盖忽略风险状态
        hasStockAlertPermission: false, // 库存预警权限标识
        
        // 库存状态选项
        stockAlertStatusOptions: [
          { value: "", label: "全部状态" },
          { value: "NO_RISK", label: "无风险" },
          { value: "STOCK_ALERT", label: "库存告急" },
          { value: "RISK_IGNORED", label: "忽略风险" }
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
        scroll: { x: 0 },
        // 新增：表格内部滚动高度
        tableBodyHeight: 0,
        // 默认索引（包含库存状态列）
        defDataIndex: ['action', 'barCode', 'materialName', 'previousPeriodStock', 'currentPeriodIn', 'currentPeriodOut', 'currentPeriodStock', 'stockAlertStatus'],
        settingDataIndex: ['action', 'barCode', 'materialName', 'previousPeriodStock', 'currentPeriodIn', 'currentPeriodOut', 'currentPeriodStock', 'stockAlertStatus'],
        // 默认列（包含库存状态列）
        defColumns: [
          {
            title: '操作',
            dataIndex: 'action',
            align: "center", 
            width: 80,
            fixed: 'left',
            scopedSlots: { customRender: 'action' },
          },
          { title: '商品编码', dataIndex: 'barCode', width: 100, fixed: 'left' },
          { title: '商品名称', dataIndex: 'materialName', width: 150, ellipsis: true, fixed: 'left' },
          { title: '上期结存', dataIndex: 'previousPeriodStock', width: 90, fixed: 'left', scopedSlots: { customRender: 'customRenderStock' } },
          { title: '本期入库', dataIndex: 'currentPeriodIn', width: 90, fixed: 'left', scopedSlots: { customRender: 'customRenderStock' } },
          { title: '本期出库', dataIndex: 'currentPeriodOut', width: 90, fixed: 'left', scopedSlots: { customRender: 'customRenderStock' } },
          { title: '本期结存', dataIndex: 'currentPeriodStock', width: 90, fixed: 'left', scopedSlots: { customRender: 'customRenderStock' } },
          { title: '库存状态', dataIndex: 'stockAlertStatus', width: 110, align: 'center', fixed: 'left', scopedSlots: { customRender: 'stockAlertStatusRender' } }
        ]

      }
    },
    computed: {
      columns() {
        const baseColumns = this.defColumns.filter(item => this.settingDataIndex.includes(item.dataIndex))
        const allColumns = [...baseColumns, ...this.dateColumns]
        return allColumns
      },
      allDataIndex() {
        return [...this.settingDataIndex, ...this.dateColumns.map(col => col.dataIndex)]
      },
      // 分页配置
      paginationConfig() {
        return this.ipagination
      }
    },
    created() {
      this.generateDateColumns()
      this.loadStockData()
    },
    beforeDestroy() {
      // 清理内存
      if (this.loadingRequest) {
        this.loadingRequest.abort()
      }
      if (this.debouncedLoadData) {
        clearTimeout(this.debouncedLoadData)
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
        if (!this.queryParam.createTimeRange || this.queryParam.createTimeRange.length !== 2) {
          this.dateColumns = []
          return
        }

        const [beginDate, endDate] = this.queryParam.createTimeRange
        const validation = this.validateDateRange(beginDate, endDate)
        
        let actualBeginDate = beginDate
        let actualEndDate = endDate
        
        if (!validation.valid && validation.adjustedRange) {
          [actualBeginDate, actualEndDate] = validation.adjustedRange
          this.queryParam.createTimeRange = validation.adjustedRange
        }

        const dates = this.generateDateRange(actualBeginDate, actualEndDate)
        
        this.dateColumns = dates.map(date => ({
          title: moment(date).format('MM-DD'),
          dataIndex: `out_${date}`,
          width: 60,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        
        // 更新滚动宽度
        this.scroll.x = (this.dateColumns.length * 60)
      },

      // 防抖处理的数据加载
      debouncedLoadData: null,

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
          stockAlertStatus: ""
        }
        this.generateDateColumns()
        this.searchQuery()
      },

      // 防抖处理的数据加载
      debouncedLoadStockData() {
        if (this.debouncedLoadData) {
          clearTimeout(this.debouncedLoadData)
        }
        this.debouncedLoadData = setTimeout(() => {
          this.loadStockData()
        }, 500)
      },




      // 日期变化处理
      onDateChange(dates, dateStrings) {
        this.queryParam.createTimeRange = dates
        this.generateDateColumns()
        if (dates && dates.length === 2) {
          this.debouncedLoadStockData()
        }
      },
      onDateOk(dates) {
        console.log('选择的日期: ', dates)
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
        
        this.loading = true
        const params = {
          currentPage: this.ipagination.current,
          pageSize: this.ipagination.pageSize,
          materialParam: this.queryParam.materialParam || '',
          stockAlertStatus: this.queryParam.stockAlertStatus || ''
        }
        
        // 如果有日期范围参数，添加到请求中
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
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

        // 使用requestAnimationFrame分批处理，避免UI阻塞
        const batchSize = 10
        let index = 0
        
        const processBatch = () => {
          const endIndex = Math.min(index + batchSize, this.dataSource.length)
          
          for (let i = index; i < endIndex; i++) {
            const item = this.dataSource[i]
            const dailyData = this.dailyOutData[item.barCode] || {}
            
            // 为每个日期列添加出库数据
            this.dateColumns.forEach(column => {
              const date = column.dataIndex.replace('out_', '')
              this.$set(item, column.dataIndex, dailyData[date] || 0)
            })
          }
          
          index = endIndex
          if (index < this.dataSource.length) {
            requestAnimationFrame(processBatch)
          }
        }
        
        processBatch()
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
      });
      // 监听窗口resize
      this._resizeHandler = () => {
        this.calcTableBodyHeight();
      };
      window.addEventListener('resize', this._resizeHandler);
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
</style>