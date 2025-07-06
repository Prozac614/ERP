<template>
  <div class="page-header-index-wide">
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
        <a-card :loading="loading" :bordered="false" title="出库统计分析">
          <div slot="extra">
            <a-range-picker
              v-model="dateRange"
              @change="onDateRangeChange"
              format="YYYY-MM-DD"
              placeholder="选择时间范围"
              style="width: 240px"
            />
          </div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="24">
      <a-col :sm="24" :md="12" :xl="12" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" title="商品库存统计">
          <a-table
            :columns="stockColumns"
            :data-source="stockData"
            :pagination="false"
            size="small"
            :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: onSelectChange }"
            :scroll="{ y: 300 }"
          >
          </a-table>
        </a-card>
      </a-col>
      <a-col :sm="24" :md="12" :xl="12" :style="{ paddingRight: '0px',marginBottom: '12px' }">
        <a-card :loading="loading" :bordered="false" title="出库数量趋势">
          <line-chart-multid
            :height="300"
            :data="outStockChartData"
            :title="'出库数量趋势'"
            :yaxisText="'数量'"
          />
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
  import { getBuyAndSaleStatistics, buyOrSalePrice, getPlatformConfigByKey, getMaterialPeriodStock } from '@/api/api'
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
        statistics: {},
        barHeight: document.documentElement.clientHeight-585,
        yaxisText: '金额',
        buyPriceData: [],
        salePriceData: [],
        retailPriceData: [],
        visitFields:['ip','visit'],
        visitInfo:[],
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
        getBuyAndSaleStatistics().then((res)=>{
          if(res.code === 200){
            this.statistics = res.data;
          }
        })
        buyOrSalePrice().then(res=>{
          if(res.code === 200){
            this.buyPriceData = res.data.buyPriceList
            this.salePriceData = res.data.salePriceList
            this.retailPriceData = res.data.retailPriceList
          }
        })
        getPlatformConfigByKey({"platformKey": "pay_fee_url"}).then((res)=> {
          if (res && res.code === 200) {
            this.payFeeUrl = res.data.platformValue
          }
        })
        this.loadStockData()
      },
      loadStockData() {
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
          }
        }).catch(error => {
          console.error('获取库存数据失败:', error)
          this.stockData = []
        })
      },
      onDateRangeChange(dates, dateStrings) {
        this.dateRange = dates
        // 根据日期范围更新图表数据
        this.updateChartData()
      },
      onSelectChange(selectedRowKeys) {
        this.selectedRowKeys = selectedRowKeys
        this.updateChartData()
      },
      updateChartData() {
        // 根据选中的商品和日期范围更新图表数据
        // 这里暂时使用模拟数据
        this.outStockChartData = [
          {
            date: '2024-01-01',
            value: 100,
            type: '商品A'
          },
          {
            date: '2024-01-02',
            value: 120,
            type: '商品A'
          },
          {
            date: '2024-01-03',
            value: 90,
            type: '商品A'
          }
        ]
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
</style>