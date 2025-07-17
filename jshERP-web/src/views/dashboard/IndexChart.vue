<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card style="margin-bottom: 16px;" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="8" :sm="24">
                <a-form-item label="商品信息" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入唛头、名称、助记码、规格、型号等信息" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="8" :sm="24">
                <a-form-item label="单据日期" :labelCol="labelCol" :wrapperCol="wrapperCol">
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
                <a-col :md="8" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                </a-col>
              </span>
            </a-row>
          </a-form>
        </div>
        
        <!-- table区域-begin -->
        <div>
          <a-card :loading="loading" :bordered="false" title="📋 商品库存明细表" class="table-card">
          <div slot="extra" class="table-actions">
            <a-button type="primary" icon="line-chart" size="small" @click="toggleChart" style="margin-right: 8px;">
              {{ showChart ? '隐藏图表' : '显示图表' }}
            </a-button>
            <a-button size="small" @click="selectAll" style="margin-right: 8px;">
              全选
            </a-button>
            <a-button size="small" @click="selectInvert" style="margin-right: 8px;">
              反选
            </a-button>
            <a-button size="small" @click="clearSelection" style="margin-right: 8px;">
              清空选择
            </a-button>
            <a-tag color="processing" v-if="selectedRowKeys.length > 0">
              已选择 {{ selectedRowKeys.length }}/10 个商品
            </a-tag>
            <a-tag color="default" v-if="selectedRowKeys.length === 0">
              最多可选择10个商品
            </a-tag>
          </div>
          
          <a-table
            :columns="stockColumns"
            :data-source="stockData"
            :pagination="ipagination"
            size="small"
            :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: onSelectChange }"
            :scroll="{ y: 350 }"
            class="stock-table"
            :loading="loading"
            @change="handleTableChange"
          >
          </a-table>
          
            <div class="table-footer-tip">
              <a-icon type="bulb" style="color: #faad14; margin-right: 4px;" />
              💡 商品库存明细表
            </div>
          </a-card>
        </div>
        <!-- table区域-end -->
        
        <!-- 暂时隐藏趋势图表功能
        <a-row :gutter="24" v-if="showChart">
          <a-col :sm="24" :md="24" :xl="24" :style="{ paddingRight: '0px',marginBottom: '12px' }">
            <a-card :loading="loading" :bordered="false" title="📈 出库数量趋势图表" class="chart-card">
              <div slot="extra" class="chart-extra">
                <span style="margin-right: 8px;">日期：</span>
                <a-range-picker
                  v-model="dateRange"
                  @change="onDateRangeChange"
                  format="YYYY-MM-DD"
                  placeholder="选择时间范围"
                  style="width: 240px; margin-right: 12px;"
                />
                <a-button type="primary" icon="reload" @click="loadStockData" size="small" style="margin-right: 8px;">
                  刷新
                </a-button>
                <a-tag color="blue" v-if="selectedRowKeys.length > 0">
                  已选择 {{ selectedRowKeys.length }}/10 个商品
                </a-tag>
                <a-tag color="default" v-else>
                  请选择商品查看趋势（最多10个）
                </a-tag>
              </div>
              <div class="chart-container">
                <line-chart-multid
                  :height="450"
                  :dataSource="outStockChartData"
                  :title="'出库数量趋势'"
                  :yaxisText="'数量'"
                  :fields="outStockChartFields"
                />
              </div>
            </a-card>
          </a-col>
        </a-row>
        -->
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import ChartCard from '@/components/ChartCard'
  import ACol from "ant-design-vue/es/grid/Col"
  import ATooltip from "ant-design-vue/es/tooltip/Tooltip"
  import MiniArea from '@/components/chart/MiniArea'
  import MiniBar from '@/components/chart/MiniBar'
  import MiniProgress from '@/components/chart/MiniProgress'
  import Bar from '@/components/chart/Bar'
  import LineChartMultid from '@/components/chart/LineChartMultid'
  import HeadInfo from '@/components/tools/HeadInfo.vue'
  import Trend from '@/components/Trend'
  import { getPlatformConfigByKey, getMaterialPeriodStock, getDailyOutStock } from '@/api/api'
  import { handleIntroJs } from "@/utils/util"
  import { getAction,postAction } from '../../api/manage'
  import moment from 'moment'

  export default {
    name: "IndexChart",
    components: {
      ATooltip,
      ACol,
      ChartCard,
      MiniArea,
      MiniBar,
      MiniProgress,
      Bar,
      Trend,
      LineChartMultid,
      HeadInfo
    },
    data() {
      return {
        hovered: false,
        systemTitle: window.SYS_TITLE,
        systemUrl: window.SYS_URL,
        loading: true,
        center: null,
        hasExpire: false,
        payFeeUrl: '',
        tenant: {
          type: '',
          expireTime: '',
          userCurrentNum: '',
          userNumLimit: '',
          tenantId: ''
        },
        // 查询条件
        queryParam: {
          materialParam: "",
          createTimeRange: [moment().subtract(6, 'months'), moment()],
        },
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 新增的数据字段
        showChart: false, // 控制图表显示状态
        dateRange: [moment().subtract(6, 'months'), moment()],
        stockData: [],
        selectedRowKeys: [],
        outStockChartData: [],
        outStockChartFields: [],
        // 分页配置
        ipagination: {
          current: 1,
          pageSize: 10,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total, range) => {
            return range[0] + "-" + range[1] + " 共" + total + "条"
          },
          showQuickJumper: true,
          showSizeChanger: true,
          total: 0
        },
        stockColumns: [
          {
            title: '商品名称',
            dataIndex: 'materialName',
            key: 'materialName',
            width: 150
          },
          {
            title: '唛头',
            dataIndex: 'barCode',
            key: 'barCode',
            width: 120
          },
          {
            title: '上期结存',
            dataIndex: 'previousPeriodStock',
            key: 'previousPeriodStock',
            width: 100,
            align: 'right'
          },
          {
            title: '本期结存',
            dataIndex: 'currentPeriodStock',
            key: 'currentPeriodStock',
            width: 100,
            align: 'right'
          },
          {
            title: '上期出库',
            dataIndex: 'previousPeriodOut',
            key: 'previousPeriodOut',
            width: 100,
            align: 'right'
          },
          {
            title: '本期出库',
            dataIndex: 'currentPeriodOut',
            key: 'currentPeriodOut',
            width: 100,
            align: 'right'
          }
        ]
      }
    },
    created() {
      setTimeout(() => {
        this.loading = !this.loading
      }, 1000)
      this.initInfo()
      this.initWithTenant()
    },
    mounted() {
      handleIntroJs('indexChart', 1)
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
          createTimeRange: [moment().subtract(6, 'months'), moment()],
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
      initInfo () {
        // 移除不需要的统计数据获取
        // getBuyAndSaleStatistics().then((res)=>{
        //   if(res.code === 200){
        //     this.statistics = res.data;
        //   }
        // })
        // buyOrSalePrice().then(res=>{
        //   if(res.code === 200){
        //     this.buyPriceData = res.data.buyPriceList
        //     this.salePriceData = res.data.salePriceList
        //     this.retailPriceData = res.data.retailPriceList
        //   }
        // })
        getPlatformConfigByKey({"platformKey": "pay_fee_url"}).then((res)=> {
          if (res && res.code === 200) {
            this.payFeeUrl = res.data.platformValue
          }
        })
        this.loadStockData()
      },
      loadStockData(page) {
        // 如果传入页码参数，则更新当前页码
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
        
        getMaterialPeriodStock(params).then(res => {
          if (res.code === 200 && res.data) {
            this.stockData = res.data.rows.map((item, index) => ({
              key: item.materialId || index,
              materialId: item.materialId,
              materialName: item.materialName || '未知商品',
              barCode: item.barCode || '无',
              previousPeriodStock: parseInt(item.previousPeriodStock || 0),
              currentPeriodStock: parseInt(item.currentPeriodStock || 0),
              previousPeriodOut: parseInt(item.previousPeriodOut || 0),
              currentPeriodOut: parseInt(item.currentPeriodOut || 0)
            }))
            
            // 更新分页信息
            this.ipagination.total = res.data.total
            
            // 默认选中前5个商品（仅在首次加载时）
            if (this.ipagination.current === 1 && this.stockData.length > 0) {
              this.selectedRowKeys = this.stockData.slice(0, Math.min(5, this.stockData.length)).map(item => item.key)
              this.updateChartData()
            }
          }
        }).catch(error => {
          console.error('获取库存数据失败:', error)
          this.stockData = []
          this.$message.error('获取库存数据失败')
        }).finally(() => {
          this.loading = false
        })
      },
      onDateRangeChange(dates, dateStrings) {
        this.dateRange = dates
        // 根据日期范围更新数据
        this.loadStockData()
        this.updateChartData()
      },
      onSelectChange(selectedRowKeys) {
        // 限制最多选择10个商品
        if (selectedRowKeys.length > 10) {
          this.$message.warning('最多只能选择10个商品进行对比，当前已自动限制为前10个')
          // 保留前10个选择
          this.selectedRowKeys = selectedRowKeys.slice(0, 10)
        } else {
          this.selectedRowKeys = selectedRowKeys
        }
        this.updateChartData()
      },
      updateChartData() {
        // 根据选中的商品更新图表数据
        if (this.selectedRowKeys.length === 0) {
          this.outStockChartData = []
          this.outStockChartFields = []
          return
        }
        
        // 获取选中的商品数据
        const selectedItems = this.stockData.filter(item => 
          this.selectedRowKeys.includes(item.key)
        )
        
        // 构建商品ID参数
        const materialIds = selectedItems.map(item => item.materialId).join(',')
        
        // 构建日期参数
        let beginTime = ''
        let endTime = ''
        if (this.dateRange && this.dateRange.length === 2) {
          beginTime = this.dateRange[0].format('YYYY-MM-DD')
          endTime = this.dateRange[1].format('YYYY-MM-DD')
        } else {
          // 默认显示过去30天
          const endDate = new Date()
          const beginDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
          beginTime = beginDate.toISOString().split('T')[0]
          endTime = endDate.toISOString().split('T')[0]
        }
        
        // 调用API获取真实数据
        getDailyOutStock({
          materialIds: materialIds,
          beginTime: beginTime,
          endTime: endTime
        }).then(res => {
          if (res.code === 200 && res.data) {
            // 处理API返回的数据
            this.processChartData(res.data, selectedItems, beginTime, endTime)
          } else {
            console.error('获取出库数据失败:', res.data)
            this.outStockChartData = []
            this.outStockChartFields = []
          }
        }).catch(error => {
          console.error('获取出库数据失败:', error)
          this.outStockChartData = []
          this.outStockChartFields = []
        })
      },
      
      processChartData(apiData, selectedItems, beginTime, endTime) {
        // 生成完整的日期范围
        const dates = []
        const startDate = new Date(beginTime)
        const endDate = new Date(endTime)
        
        const currentDate = new Date(startDate)
        while (currentDate <= endDate) {
          dates.push(currentDate.toISOString().split('T')[0])
          currentDate.setDate(currentDate.getDate() + 1)
        }
        
        // 将API数据转换为以日期为key的Map
        const dataMap = new Map()
        apiData.forEach(item => {
          const key = `${item.outDate}_${item.barCode}`
          dataMap.set(key, item.outQuantity)
        })
        
        // 构建图表数据
        const chartData = []
        dates.forEach(date => {
          const dayData = { type: date }
          
          selectedItems.forEach(item => {
            const barCode = item.barCode || 'unknown'
            const key = `${date}_${barCode}`
            dayData[barCode] = dataMap.get(key) || 0
          })
          
          chartData.push(dayData)
        })
        
        this.outStockChartData = chartData
        this.outStockChartFields = selectedItems.map(item => item.barCode || 'unknown')
      },
      initWithTenant() {
        getAction("/user/infoWithTenant",{}).then(res=>{
          if(res && res.code === 200) {
            this.tenant = res.data
            let currentTime = new Date(); //新建一个日期对象，默认现在的时间
            let expireTime = new Date(res.data.expireTime); //设置过去的一个时间点，"yyyy-MM-dd HH:mm:ss"格式化日期
            let difftime = expireTime - currentTime; //计算时间差
            //如果距离到期还剩5天就进行提示续费
            if(difftime<86400000*5) {
              this.hasExpire = true
              //针对免费租户发送试用到期的消息提醒
              if(res.data.type === '0') {
                //先检查有无发送过，只发送一次
                getAction("/msg/getMsgCountByType",{'type': '试用到期'}).then(res=>{
                  if(res && res.code === 200) {
                    if(res.data.count === 0) {
                      //发送消息
                      let msgParam = {
                        'msgTitle': '试用到期提醒',
                        'msgContent': '试用期即将结束，请您及时续费，过期将会影响正常使用！',
                        'type': '试用到期',
                        'userId': this.tenant.tenantId
                      }
                      postAction("/msg/add",msgParam).then(res=>{
                        if(res && res.code === 200) {

                        }
                      })
                    }
                  }
                })
              }
            }
          }
        })
      },
      handleHoverChange(visible) {
        this.hovered = visible
      },
      showWeixinSpan() {
        let host = window.location.host
        if(host === 'cloud.gyjerp.com') {
          return true
        } else {
          return false
        }
      },
      selectAll() {
        const allKeys = this.stockData.map(item => item.key)
        if (allKeys.length > 10) {
          this.$message.warning('当前页商品数量超过10个，将只选择前10个商品')
          this.selectedRowKeys = allKeys.slice(0, 10)
        } else {
          this.selectedRowKeys = allKeys
        }
        this.updateChartData()
      },
      selectInvert() {
        const allKeys = this.stockData.map(item => item.key)
        this.selectedRowKeys = allKeys.filter(key => !this.selectedRowKeys.includes(key))
        this.updateChartData()
      },
      clearSelection() {
        this.selectedRowKeys = []
        this.updateChartData()
      },
      handleTableChange(pagination, filters, sorter) {
        // 处理分页变化
        this.ipagination = pagination
        this.loadStockData()
      },
      toggleChart() {
        this.showChart = !this.showChart
        // 显示图表时自动渲染数据
        if (this.showChart) {
          this.updateChartData()
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

  .circle-cust{
    position: relative;
    top: 28px;
    left: -100%;
  }
  .extra-wrapper {
    line-height: 55px;
    padding-right: 24px;

    .extra-item {
      display: inline-block;
      margin-right: 24px;

      a {
        margin-left: 24px;
      }
    }
  }
  /* 首页访问量统计 */
  .head-info {
    position: relative;
    text-align: left;
    padding: 0 32px 0 0;
    min-width: 125px;
    &.center {
      text-align: center;
      padding: 0 32px;
    }
    span {
      color: rgba(0, 0, 0, .45);
      display: inline-block;
      font-size: .95rem;
      line-height: 42px;
      margin-bottom: 4px;
    }
    p {
      line-height: 42px;
      margin: 0;
      a {
        font-weight: 600;
        font-size: 1rem;
      }
    }
  }
  
  /* 仪表板样式 */
  .chart-card {
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    margin-bottom: 16px;
    
    .ant-card-head {
      border-bottom: 1px solid #e8e8e8;
      
      .ant-card-head-title {
        font-size: 16px;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.85);
      }
    }
    
    .chart-extra {
      display: flex;
      align-items: center;
      gap: 8px;
      
      span {
        color: rgba(0, 0, 0, 0.65);
        font-size: 14px;
        font-weight: 500;
      }
      
      .ant-btn {
        border-radius: 4px;
        
        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }
      }
    }
    
    .chart-container {
      padding: 16px 0;
      min-height: 400px;
      display: flex;
      align-items: center;
      justify-content: center;
      
      // 当没有数据时显示占位符
      &:empty::before {
        content: "请选择商品查看出库趋势";
        color: rgba(0, 0, 0, 0.45);
        font-size: 14px;
      }
    }
  }
  
  .table-card {
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    margin-bottom: 16px;
    
    .ant-card-head {
      border-bottom: 1px solid #e8e8e8;
      
      .ant-card-head-title {
        font-size: 16px;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.85);
      }
    }
    
    .table-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .ant-btn {
        border-radius: 4px;
        
        &:hover {
          transform: translateY(-1px);
        }
      }
    }
    
    .stock-table {
      .ant-table-thead > tr > th {
        background-color: #fafafa;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.85);
      }
      
      .ant-table-tbody > tr {
        &:hover {
          background-color: #f5f5f5;
        }
        
        &.ant-table-row-selected {
          background-color: #e6f7ff;
        }
      }
      
      .ant-table-selection-column {
        width: 40px;
      }
    }
    
    .table-footer-tip {
      text-align: center;
      padding: 16px 0;
      color: rgba(0, 0, 0, 0.45);
      background-color: #fafafa;
      border-radius: 0 0 8px 8px;
      margin-top: 16px;
      font-size: 13px;
      
      .anticon {
        margin-right: 4px;
      }
    }
  }
  
  /* 响应式设计 */
  @media (max-width: 768px) {
    .chart-card {
      .chart-extra {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
        
        .ant-picker {
          width: 100% !important;
        }
        
        .ant-tag {
          margin-top: 4px;
        }
        
        .ant-btn {
          width: auto;
          margin-right: 4px;
        }
      }
    }
    
    .table-actions {
      flex-direction: column;
      gap: 8px;
      
      .ant-btn {
        width: 100%;
      }
    }
    
    .chart-card .chart-container {
      min-height: 300px;
    }
  }
  
  /* 动画效果 */
  .ant-card {
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
  }
  
  .ant-tag {
    border-radius: 12px;
    padding: 2px 8px;
    font-size: 12px;
  }
  
  .ant-btn {
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-1px);
    }
  }
</style>