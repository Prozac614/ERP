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
          <a-button 
            :type="showAllProducts ? 'default' : 'primary'" 
            :icon="showAllProducts ? 'table' : 'unordered-list'" 
            @click="toggleShowAllProducts">
            {{ showAllProducts ? '分页显示' : '展示所有商品' }}
          </a-button>
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
          <a-table
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

      // 加载所有商品数据
      loadAllProducts() {
        this.loading = true
        
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

        this.loadingRequest = getAction('/depotItem/getMaterialStockWithDailyOut', params)
        this.loadingRequest.then((res) => {
          if (res.code === 200) {
            this.processDataResponse(res.data)
            this.$message.success(`已加载 ${this.dataSource.length} 个商品`)
          } else {
            this.$message.error(res.data || '数据加载失败')
            this.showAllProducts = false // 失败时恢复分页模式
          }
        }).catch((error) => {
          if (error.name !== 'AbortError') {
            console.error('获取所有商品数据失败:', error)
            this.$message.error('数据加载失败')
            this.showAllProducts = false // 失败时恢复分页模式
          }
        }).finally(() => {
          this.loading = false
          this.loadingRequest = null
        })
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

        // 使用新的合并API
        this.loadingRequest = getAction('/depotItem/getMaterialStockWithDailyOut', params)
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