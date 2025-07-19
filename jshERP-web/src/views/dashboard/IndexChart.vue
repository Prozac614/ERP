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
          <a-button icon="reload" @click="refreshData">刷新数据</a-button>
          <a-button icon="warning" @click="showLowStockAlert">低库存预警</a-button>

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
                  <template v-for="(dateCol,index) in dateColumns" :key="dateCol.dataIndex">
                    <a-col :span="6">
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
          <!-- 回到可靠的虚拟滚动表格 -->
          <VirtualTable
            v-if="showAllProducts && !loading && dataSource.length > 0"
            ref="virtualTable"
            :dataSource="dataSource"
            :columns="columns"
            :containerHeight="600"
            :rowHeight="54"
            :showPerformanceInfo="false"
            rowKey="id"
            @row-click="handleRowClick"
          />
          
          <!-- 无数据提示 -->
          <div v-if="showAllProducts && !loading && dataSource.length === 0" 
               style="text-align: center; padding: 50px; color: #999;">
            <a-icon type="inbox" style="font-size: 48px; margin-bottom: 16px;" />
            <p style="font-size: 16px;">暂无数据</p>
            <p>请检查查询条件或联系管理员</p>
          </div>
          
          <!-- 简化的状态信息 -->
          <div v-if="showAllProducts && !loading" style="margin: 10px; padding: 10px; background: #e6f7ff; border: 1px solid #91d5ff; border-radius: 4px;">
            <p><strong>📊 数据状态:</strong></p>
            <p>✅ 数据行数: {{ dataSource.length }}</p>
            <p>✅ 列数: {{ columns.length }} (基础列: {{ defColumns.length }}, 日期列: {{ dateColumns.length }})</p>
            <p>✅ 日期范围: {{ queryParam.createTimeRange && queryParam.createTimeRange.length === 2 ? queryParam.createTimeRange[0].format('MM-DD') + ' 至 ' + queryParam.createTimeRange[1].format('MM-DD') : '未设置' }}</p>
          </div>
          
          <!-- 普通分页表格 (正常分页模式) -->
          <a-table
            v-else
            ref="table"
            size="middle"
            bordered
            rowKey="id"
            :columns="columns"
            :dataSource="dataSource"
            :components="handleDrag(columns)"
            :pagination="paginationConfig"
            :scroll="scroll"
            :loading="loading"
            :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <a @click="viewChart(record)">查看图表</a>
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
          </a-table>
          
          <!-- 状态提示 -->
          <div v-if="showAllProducts" style="margin-top: 16px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px;">
            <a-icon type="info-circle" style="color: #52c41a; margin-right: 8px;" />
            <span style="color: #389e0d;">
              当前显示所有商品，共 {{ dataSource.length }} 个商品
              <span v-if="dateColumns.length > 0">，{{ dateColumns.length }} 个日期列</span>
            </span>
          </div>

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
  import { getAction, postAction } from '@/api/manage'
  import { startStockWarningCalculation, getTaskStatus } from '@/api/stockWarning'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import VirtualTable from '@/components/VirtualTable'
  import VirtualTableOptimized from '@/components/VirtualTableOptimized'
  import VirtualTableUltraOptimized from '@/components/VirtualTableUltraOptimized'
  import StockChartModal from '@/components/charts/StockChartModal'
  import StockWarningProgressModal from '@/components/StockWarningProgressModal'
  import Vue from 'vue'

  export default {
    name: "IndexChart",
    components: {
      JEllipsis,
      VirtualTable,
      VirtualTableOptimized,
      VirtualTableUltraOptimized,
      StockChartModal,
      StockWarningProgressModal
    },
    data () {
      return {
        // 查询条件
        queryParam: {
          materialParam: "",
          createTimeRange: [moment().subtract(1, 'months'), moment()]
        },
        // 性能优化相关
        dataCache: new Map(),
        loadingRequest: null,
        dailyOutData: {},
        dateColumns: [],
        // 展示所有商品控制
        showAllProducts: false,
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
        // 轻量级数据存储
        lightweightData: {
          rows: [], // 只存储必要的行数据
          columnMapping: new Map(), // 列名映射
          cellValueCache: new Map() // 单元格值缓存
        },
        // 图表弹窗控制
        chartModal: {
          visible: false,
          currentMaterial: {}
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
            width: 120,
            scopedSlots: { customRender: 'action' },
          },
          { title: '商品编码', dataIndex: 'barCode', width: 120 },
          { title: '商品名称', dataIndex: 'materialName', width: 200, ellipsis: true },
          { title: '本期结存', dataIndex: 'currentPeriodStock', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '上期结存', dataIndex: 'previousPeriodStock', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '本期出库', dataIndex: 'currentPeriodOut', width: 120, scopedSlots: { customRender: 'customRenderStock' } },
          { title: '上期出库', dataIndex: 'previousPeriodOut', width: 120, scopedSlots: { customRender: 'customRenderStock' } }
        ],

        // 库存预警计算相关
        calculationLoading: false,
        calculationTaskId: null,
        progressModal: {
          visible: false,
          taskStatus: {}
        }

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
      // 动态分页配置
      paginationConfig() {
        if (this.showAllProducts) {
          return false // 展示所有商品时禁用分页
        }
        return this.ipagination
      },
      
      // 总行数（用于超级虚拟表格）
      totalRows() {
        const count = this.dataSource.length
        console.log(`总行数: ${count}`)
        return count
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
      this.dataCache.clear()
      
      // 🚨 重要：清理超大数据集的内存
      this.clearMemoryCache()
      this.lightweightData.rows = []
      this.lightweightData.columnMapping.clear()
      this.dataSource = []
      
      // 强制垃圾回收（如果可用）
      if (window.gc) {
        console.log('🗑️ 触发垃圾回收')
        window.gc()
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
          width: 80,
          align: 'center',
          scopedSlots: { customRender: 'dailyOutRender' }
        }))
        
        // 更新滚动宽度
        this.scroll.x = 800 + (this.dateColumns.length * 80)
      },

      // 防抖处理的数据加载
      debouncedLoadData: null,

      // 切换展示所有商品模式
      toggleShowAllProducts() {
        this.showAllProducts = !this.showAllProducts
        
        if (this.showAllProducts) {
          // 切换到展示所有商品模式时，给出性能警告
          this.$confirm({
            title: '性能提示',
            content: '展示所有商品可能会影响页面性能，特别是在商品数量较多或选择的日期范围较大时。确定要继续吗？',
            okText: '继续',
            cancelText: '取消',
            onOk: () => {
              this.loadAllProducts()
            },
            onCancel: () => {
              this.showAllProducts = false
            }
          })
        } else {
          // 切换回分页模式
          this.ipagination.current = 1
          this.debouncedLoadStockData()
        }
      },

      // 加载所有商品数据（优化版）
      loadAllProducts() {
        // 显示确认对话框，警告用户大数据量加载
        this.$confirm({
          title: '加载大量数据',
          content: '即将加载所有商品数据，数据量较大可能需要一些时间。确定继续吗？',
          okText: '确定加载',
          cancelText: '取消',
          onOk: () => {
            this.performLoadAllProducts()
          },
          onCancel: () => {
            this.showAllProducts = false
          }
        })
      },

      // 执行加载所有商品数据
      performLoadAllProducts() {
        this.loading = true
        
        // 显示加载进度
        const loadingMessage = this.$message.loading('正在加载大量数据，请稍候...', 0)
        
        const params = {
          currentPage: 1,
          pageSize: 10000, // 设置一个很大的pageSize来获取所有数据
          materialParam: this.queryParam.materialParam || ''
        }
        
        // 如果有日期范围参数，添加到请求中
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
        }

        // 取消之前的请求
        if (this.loadingRequest) {
          this.loadingRequest.abort()
        }

        this.loadingRequest = getAction('/depotItem/getMaterialStockWithDailyOutOptimized', params)
        this.loadingRequest.then(async (res) => {
          if (res.code === 200) {
            loadingMessage()
            
            // 显示数据处理进度
            const processingMessage = this.$message.loading('正在处理数据，请稍候...', 0)
            
            try {
              // 分批处理数据，避免阻塞UI
              await this.processLargeDataResponse(res.data)
              processingMessage()
              
              this.$message.success(`✅ 成功加载 ${this.dataSource.length} 个商品，已启用虚拟滚动优化`)
            } catch (error) {
              processingMessage()
              console.error('数据处理失败:', error)
              this.$message.error('数据处理失败')
              this.showAllProducts = false
            }
          } else {
            loadingMessage()
            this.$message.error(res.data || '数据加载失败')
            this.showAllProducts = false
          }
        }).catch((error) => {
          loadingMessage()
          if (error.name !== 'AbortError') {
            console.error('获取所有商品数据失败:', error)
            this.$message.error('数据加载失败')
            this.showAllProducts = false
          }
        }).finally(() => {
          this.loading = false
          this.loadingRequest = null
        })
      },

      // 回到可靠的数据处理方法
      async processLargeDataResponse(data) {
        // 使用原来可靠的方法，避免过度优化
        this.processDataResponse(data)
      },

      // 查询方法
      searchQuery() {
        if (this.showAllProducts) {
          this.loadAllProducts()
        } else {
          this.ipagination.current = 1
          this.debouncedLoadStockData()
        }
      },
      // 重置查询
      searchReset() {
        this.queryParam = {
          materialParam: "",
          createTimeRange: [moment().subtract(1, 'months'), moment()]
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

      // 刷新数据
      refreshData() {
        // 清除缓存
        this.dataCache.clear()
        
        if (this.showAllProducts) {
          this.loadAllProducts()
        } else {
          this.loadStockData(1)
        }
      },

      // 日期变化处理
      onDateChange(dates, dateStrings) {
        this.queryParam.createTimeRange = dates
        this.generateDateColumns()
        if (dates && dates.length === 2) {
          if (this.showAllProducts) {
            this.loadAllProducts()
          } else {
            this.debouncedLoadStockData()
          }
        }
      },
      onDateOk(dates) {
        console.log('选择的日期: ', dates)
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
          materialParam: this.queryParam.materialParam || ''
        }
        
        // 如果有日期范围参数，添加到请求中
        if (this.queryParam.createTimeRange && this.queryParam.createTimeRange.length === 2) {
          params.beginTime = this.queryParam.createTimeRange[0].format('YYYY-MM-DD')
          params.endTime = this.queryParam.createTimeRange[1].format('YYYY-MM-DD')
        }

        // 检查缓存
        const cacheKey = JSON.stringify(params)
        const cached = this.dataCache.get(cacheKey)
        if (cached && (Date.now() - cached.timestamp < 300000)) { // 5分钟缓存
          this.processDataResponse(cached.data)
          this.loading = false
          return
        }

        // 使用高性能优化API
        this.loadingRequest = getAction('/depotItem/getMaterialStockWithDailyOutOptimized', params)
        this.loadingRequest.then((res) => {
          if (res.code === 200) {
            // 缓存结果
            this.dataCache.set(cacheKey, {
              data: res.data,
              timestamp: Date.now()
            })
            
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
        this.performanceStats = data.performanceStats || {}
        
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
        if (!this.showAllProducts) {
          this.ipagination = pagination
          this.loadStockData()
        }
        // 在展示所有商品模式下，不处理分页变化
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
          previousPeriodOut: materialRecord.previousPeriodOut
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
        this.$message.info('导出库存数据功能')
      },
      showLowStockAlert() {
        this.$message.info('显示低库存预警')
      },



      // 优化虚拟表格相关方法
      handleCellClick(cellInfo) {
        // 处理单元格点击事件
        console.log('单元格点击:', cellInfo)
        // 这里可以添加单元格点击逻辑，如显示详情等
      },

      // 优化数据处理 - 分批加载
      processDataInBatches(rawData, batchSize = 50) {
        return new Promise((resolve) => {
          const result = []
          let index = 0
          
          const processBatch = () => {
            const endIndex = Math.min(index + batchSize, rawData.length)
            
            for (let i = index; i < endIndex; i++) {
              result.push(rawData[i])
            }
            
            index = endIndex
            
            if (index < rawData.length) {
              // 使用 requestIdleCallback 或 setTimeout 避免阻塞UI
              if (window.requestIdleCallback) {
                requestIdleCallback(processBatch)
              } else {
                setTimeout(processBatch, 0)
              }
            } else {
              resolve(result)
            }
          }
          
          processBatch()
        })
      },

      // 超轻量级数据获取函数（关键优化）
      getCellDataOptimized(rowIndex, columnKey) {
        // 缓存键
        const cacheKey = `${rowIndex}-${columnKey}`
        
        // 优先从缓存获取
        if (this.lightweightData.cellValueCache.has(cacheKey)) {
          return this.lightweightData.cellValueCache.get(cacheKey)
        }
        
        // 从数据源获取（优先使用dataSource，保证兼容性）
        const row = this.dataSource[rowIndex]
        if (!row) {
          console.warn(`❌ 行数据不存在: rowIndex=${rowIndex}, total=${this.dataSource.length}`)
          return '-'
        }
        
        let value = row[columnKey]
        
        // 调试特定行的数据
        if (rowIndex <= 2) {
          console.log(`🔍 单元格数据获取 [${rowIndex}, ${columnKey}]:`, value)
        }
        
        // 数据格式化（最小化处理）
        if (typeof value === 'number') {
          if (columnKey.includes('out_') && value === 0) {
            value = '-'
          } else if (typeof value === 'number' && value !== 0) {
            value = value.toFixed(2)
          }
        } else if (value === null || value === undefined || value === '') {
          value = '-'
        }
        
        // 缓存结果（限制缓存大小防止内存泄漏）
        if (this.lightweightData.cellValueCache.size < 50000) {
          this.lightweightData.cellValueCache.set(cacheKey, value)
        }
        
        return value
      },

      // 清理内存缓存
      clearMemoryCache() {
        this.lightweightData.cellValueCache.clear()
        // 强制垃圾回收
        if (window.gc) {
          window.gc()
        }
      },

      // 虚拟表格相关方法
      handleRowClick(record, index) {
        console.log('点击行:', record, index)
        // 可以添加行点击逻辑
      },

      // 虚拟表格滚动控制
      scrollToTop() {
        if (this.$refs.virtualTable) {
          this.$refs.virtualTable.scrollToTop()
        }
      },

      scrollToRow(rowIndex) {
        if (this.$refs.virtualTable) {
          this.$refs.virtualTable.scrollToIndex(rowIndex)
        }
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