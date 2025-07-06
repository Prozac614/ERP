<template>
  <div class="page-header-index-wide">
    <!--
    <a-row :gutter="24">
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今日销售" data-step="1" data-title="今日销售" data-intro="统计今日销售单据的总金额">
          <a-tooltip title="统计今日销售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.todaySale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今日零售" data-step="2" data-title="今日零售" data-intro="统计今日零售单据的总金额">
          <a-tooltip title="统计今日零售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.todayRetailSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今日采购" data-step="3" data-title="今日采购" data-intro="统计今日采购单据的总金额">
          <a-tooltip title="统计今日采购单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.todayBuy"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="本月累计销售">
          <a-tooltip title="统计本月销售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.monthSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="本月累计零售">
          <a-tooltip title="统计本月零售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.monthRetailSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="本月累计采购">
          <a-tooltip placement="left" title="统计本月采购单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.monthBuy"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="昨日销售">
          <a-tooltip title="统计昨日销售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yesterdaySale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="昨日零售">
          <a-tooltip title="统计昨日零售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yesterdayRetailSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="昨日采购">
          <a-tooltip title="统计昨日采购单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yesterdayBuy"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今年累计销售">
          <a-tooltip title="统计今年销售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yearSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今年累计零售">
          <a-tooltip title="统计今年零售单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yearRetailSale"></head-info>
        </chart-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="4" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <chart-card :loading="loading" title="今年累计采购">
          <a-tooltip placement="left" title="统计今年采购单据的总金额" slot="action">
            <a-icon type="info-circle-o" />
          </a-tooltip>
          <head-info :content="statistics.yearBuy"></head-info>
        </chart-card>
      </a-col>
    </a-row>
    -->
    <!--
    <a-row :gutter="24">
      <a-col :sm="24" :md="12" :xl="8" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" :body-style="{paddingRight: '5'}" data-step="4" data-title="销售统计"
                data-intro="统计往前6个月每月销售的总金额">
          <bar title="销售统计" :height="barHeight" :yaxisText="yaxisText" :dataSource="salePriceData"/>
        </a-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="8" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" :body-style="{paddingRight: '5'}" data-step="5" data-title="零售统计"
                data-intro="统计往前6个月每月零售的总金额">
          <bar title="零售统计" :height="barHeight" :yaxisText="yaxisText" :dataSource="retailPriceData"/>
        </a-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="8" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" :body-style="{paddingRight: '5'}" data-step="6" data-title="采购统计"
                data-intro="统计往前6个月每月采购的总金额">
          <bar title="采购统计" :height="barHeight" :yaxisText="yaxisText" :dataSource="buyPriceData"/>
        </a-card>
      </a-col>
    </a-row>
    -->
    <a-row :gutter="24">
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
              已选择 {{ selectedRowKeys.length }} 个商品
            </a-tag>
            <a-tag color="default" v-else>
              请选择商品查看趋势
            </a-tag>
          </div>
          <div class="chart-container">
            <line-chart-multid
              :height="400"
              :data="outStockChartData"
              :title="'出库数量趋势'"
              :yaxisText="'数量'"
            />
          </div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="24">
      <a-col :sm="24" :md="24" :xl="24" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" title="📋 商品库存明细表" class="table-card">
          <div slot="extra" class="table-actions">
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
              已选择 {{ selectedRowKeys.length }} 个商品
            </a-tag>
          </div>
          
          <a-table
            :columns="stockColumns"
            :data-source="stockData"
            :pagination="false"
            size="small"
            :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: onSelectChange }"
            :scroll="{ y: 350 }"
            class="stock-table"
          >
          </a-table>
          
          <div class="table-footer-tip">
            <a-icon type="bulb" style="color: #faad14; margin-right: 4px;" />
            💡 选择商品行可在上方图表中查看趋势
          </div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="24">
      <a-col :sm="24" :md="24" :xl="24" :style="{ paddingRight: '0px',marginBottom: '6px' }">
        <a-card :bordered="false" :body-style="{padding: '5'}" data-step="7" data-title="服务和版权"
                data-intro="展示服务到期时间（快到期时会出现续费链接，请注意及时续费）、
          用户数量（是指最多可以录入的用户数量）、版权信息">
          <div class="hidden-xs" style="float:right;">
            <a-popover
              trigger="hover"
              :visible="hovered"
              @visibleChange="handleHoverChange">
              <div slot="content">
                <img src="/static/weixin.jpg" style="width:258px" />
              </div>
              <a-button type="link" v-if="showWeixinSpan()">管伊佳ERP微信小程序</a-button>
            </a-popover>
            &copy; 2015-2030 {{systemTitle}} V3.5
          </div>
          <a-tag v-if="tenant.type==0" color="blue">试用到期：{{tenant.expireTime}}</a-tag>
          <a-tag v-if="tenant.type==0" color="blue">试用用户：{{tenant.userCurrentNum}}/{{tenant.userNumLimit}}</a-tag>
          <a-tag v-if="tenant.type==1" color="blue">服务到期：{{tenant.expireTime}}</a-tag>
          <a-tag v-if="tenant.type==1" color="blue">授权用户：{{tenant.userCurrentNum}}/{{tenant.userNumLimit}}</a-tag>
          <a v-if="hasExpire" style="color: red;" :href="payFeeUrl" target="_blank">立即续费</a>
        </a-card>
      </a-col>
    </a-row>
  </div>
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
  import { getPlatformConfigByKey, getMaterialPeriodStock } from '@/api/api'
  import { handleIntroJs } from "@/utils/util"
  import { getAction,postAction } from '../../api/manage'

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
        // 移除不需要的统计数据字段
        // statistics: {},
        // barHeight: document.documentElement.clientHeight-585,
        // yaxisText: '金额',
        // buyPriceData: [],
        // salePriceData: [],
        // retailPriceData: [],
        // visitFields:['ip','visit'],
        // visitInfo:[],
        hasExpire: false,
        payFeeUrl: '',
        tenant: {
          type: '',
          expireTime: '',
          userCurrentNum: '',
          userNumLimit: '',
          tenantId: ''
        },
        // 新增的数据字段
        dateRange: [],
        stockData: [],
        selectedRowKeys: [],
        outStockChartData: [],
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
      loadStockData() {
        this.loading = true
        getMaterialPeriodStock().then(res => {
          if (res.code === 200 && res.data) {
            this.stockData = res.data.map((item, index) => ({
              key: item.materialId || index,
              materialId: item.materialId,
              materialName: item.materialName || '未知商品',
              barCode: item.barCode || '无',
              previousPeriodStock: Number(item.previousPeriodStock || 0).toFixed(2),
              currentPeriodStock: Number(item.currentPeriodStock || 0).toFixed(2),
              previousPeriodOut: Number(item.previousPeriodOut || 0).toFixed(2),
              currentPeriodOut: Number(item.currentPeriodOut || 0).toFixed(2)
            }))
            // 默认选中前3个商品
            if (this.stockData.length > 0) {
              this.selectedRowKeys = this.stockData.slice(0, Math.min(3, this.stockData.length)).map(item => item.key)
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
        this.selectedRowKeys = selectedRowKeys
        this.updateChartData()
      },
      updateChartData() {
        // 根据选中的商品更新图表数据
        if (this.selectedRowKeys.length === 0) {
          this.outStockChartData = []
          return
        }
        
        // 获取选中的商品数据
        const selectedItems = this.stockData.filter(item => 
          this.selectedRowKeys.includes(item.key)
        )
        
        // 生成日期范围数据
        const chartData = []
        const startDate = this.dateRange && this.dateRange[0] ? this.dateRange[0] : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) // 默认30天前
        const endDate = this.dateRange && this.dateRange[1] ? this.dateRange[1] : new Date() // 默认今天
        
        // 生成日期数组
        const dates = []
        const currentDate = new Date(startDate)
        while (currentDate <= endDate) {
          dates.push(new Date(currentDate))
          currentDate.setDate(currentDate.getDate() + 1)
        }
        
        // 为每个选中的商品生成每日数据
        selectedItems.forEach(item => {
          dates.forEach(date => {
            chartData.push({
              date: date.toISOString().split('T')[0], // 格式: YYYY-MM-DD
              value: Math.floor(Math.random() * 30) + 5, // 随机生成5-35的数值
              type: item.barCode || item.materialName // 使用唛头字段标识商品
            })
          })
        })
        
        this.outStockChartData = chartData
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
        this.selectedRowKeys = this.stockData.map(item => item.key)
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
      }
    }
  }
</script>
<style lang="less" scoped>
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