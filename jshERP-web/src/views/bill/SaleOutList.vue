<!-- create j i s h e n g h u a -->
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
                <a-form-item label="单据编号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入单据编号" v-model="queryParam.number"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="销售店铺" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select 
                    placeholder="请选择店铺" 
                    v-model="queryParam.shopName" 
                    allow-clear 
                    showSearch 
                    :filterOption="true" 
                    optionFilterProp="children"
                    :maxTagCount="3"
                  >
                    <a-select-option v-for="(name,idx) in shopList" :key="idx" :value="name">{{ name }}</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="商品信息" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入唛头、名称、助记码、规格、型号等信息" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
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
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </a-col>
              </span>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="6" :sm="24">
                  <a-form-item label="客户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择客户" showSearch allow-clear optionFilterProp="children" v-model="queryParam.organId">
                      <a-select-option v-for="(item,index) in cusList" :key="index" :value="item.id">
                        {{ item.supplier }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="仓库名称" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择仓库" showSearch allow-clear optionFilterProp="children" v-model="queryParam.depotId">
                      <a-select-option v-for="(depot,index) in depotList" :key="index" :value="depot.id">
                        {{ depot.depotName }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="操作员" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择操作员" showSearch allow-clear optionFilterProp="children" v-model="queryParam.creator">
                      <a-select-option v-for="(item,index) in userList" :key="index" :value="item.id">
                        {{ item.userName }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="关联订单" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入关联订单" v-model="queryParam.linkNumber"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="结算账户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择结算账户" showSearch allow-clear optionFilterProp="children" v-model="queryParam.accountId">
                      <a-select-option v-for="(item,index) in accountList" :key="index" :value="item.id">
                        {{ item.name }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="有无欠款" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择有无欠款" allow-clear v-model="queryParam.hasDebt">
                      <a-select-option value="1">有欠款</a-select-option>
                      <a-select-option value="0">无欠款</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="单据状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择单据状态" allow-clear v-model="queryParam.status">
                      <a-select-option value="0">未审核</a-select-option>
                      <a-select-option value="9" v-if="!checkFlag">审核中</a-select-option>
                      <a-select-option value="1">已审核</a-select-option>
                      <a-select-option value="3">部分出库</a-select-option>
                      <a-select-option value="2">完成出库</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="单据备注" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入单据备注" v-model="queryParam.remark"></a-input>
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
          </a-form>
        </div>
        <!-- 操作按钮区域 -->
        <div class="table-operator"  style="margin-top: 5px">
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="myHandleAdd" type="primary" icon="plus">新增</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" icon="delete" @click="batchDel">删除</a-button>
          <a-button v-if="quickBtn.saleBack.indexOf(1)>-1 && btnEnableList.indexOf(1)>-1" icon="share-alt" @click="transferBill('转销售退货', quickBtn.saleBack)">转销售退货</a-button>
          <a-tooltip title="可将状态是部分出库的单据强制完成">
            <a-button v-if="inOutManageFlag && btnEnableList.indexOf(1)>-1" icon="issues-close" @click="batchForceClose">强制结单</a-button>
          </a-tooltip>
          <a-button v-if="checkFlag && btnEnableList.indexOf(2)>-1" icon="check" @click="batchSetStatus(1)">审核</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(7)>-1" icon="stop" @click="batchSetStatus(0)">反审核</a-button>
          <a-button v-if="btnEnableList.indexOf(8)>-1" icon="eye" @click="batchValidation">校验</a-button>
          <a-button v-if="isShowExcel && btnEnableList.indexOf(3)>-1" icon="download" @click="handleExport">导出</a-button>
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
          <a-tooltip placement="left" title="销售出库单可以由销售订单转过来，也可以单独创建。
          销售出库单据中的仓库列表只显示当前用户有权限的仓库。销售出库单可以使用多账户收款。
          勾选单据之后可以进行批量操作（删除、审核、反审核）" slot="action">
            <a-icon v-if="btnEnableList.indexOf(1)>-1" type="question-circle" style="font-size:20px;float:right;" />
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
            :expandedRowKeys="expandedRowKeys"
            @expand="onExpand"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <a @click="myHandleDetail(record, '销售出库', prefixNo)">查看</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleEdit(record)">编辑</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleCopyAdd(record)">复制</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a-popconfirm v-if="btnEnableList.indexOf(1)>-1" title="确定删除吗?" @confirm="() => myHandleDelete(record)">
                <a>删除</a>
              </a-popconfirm>
            </span>
            <template slot="customRenderDebt" slot-scope="value, record">
              <a-tooltip title="有收款单">
                <span style="color:green" v-if="value>0 && record.hasFinancialFlag">{{value}}</span>
              </a-tooltip>
              <a-tooltip title="暂未收款">
                <span style="color:red" v-if="value>0 && !record.hasFinancialFlag">{{value}}</span>
              </a-tooltip>
              <span v-if="value===0">{{value}}</span>
            </template>
            <template slot="customRenderStatus" slot-scope="status">
              <a-tag v-if="status == '0'" color="red">未审核</a-tag>
              <a-tag v-if="status == '1'" color="green">已审核</a-tag>
              <a-tag v-if="status == '2'" color="cyan">完成出库</a-tag>
              <a-tag v-if="status == '3'" color="blue">部分出库</a-tag>
              <a-tag v-if="status == '9'" color="orange">审核中</a-tag>
            </template>
            <a-table
              bordered
              size="small"
              slot="expandedRowRender"
              slot-scope="record"
              :loading="record.loading"
              :columns="detailColumns"
              :dataSource="record.childrens"
              :row-key="record => record.id"
              :pagination="false">
            </a-table>
          </a-table>
        </div>
        <!-- table区域-end -->
        <!-- 表单区域 -->
        <sale-out-modal ref="modalForm" @ok="modalFormOk" @close="modalFormClose"></sale-out-modal>
        <sale-back-modal ref="transferModalForm" @ok="modalFormOk" @close="modalFormClose"></sale-back-modal>
        <bill-detail ref="modalDetail" @ok="modalFormOk" @close="modalFormClose"></bill-detail>
        <bill-excel-iframe ref="billExcelIframe" @ok="modalFormOk" @close="modalFormClose"></bill-excel-iframe>
        <user-selection-modal ref="userSelectionModal" @validation-success="handleValidationSuccess" @validation-failed="handleValidationFailed"></user-selection-modal>
        <validation-differences-modal ref="validationDifferencesModal"></validation-differences-modal>
        
        <!-- 日期选择器模态框 -->
        <a-modal
          title="选择校验日期"
          :visible="validationDateVisible"
          @ok="handleDateConfirm"
          @cancel="handleDateCancel"
          okText="确定"
          cancelText="取消"
        >
          <div style="margin-bottom: 16px;">
            <p>请选择要进行交叉校验的日期：</p>
            <j-date
              v-model="selectedValidationDate"
              placeholder="请选择日期"
              dateFormat="YYYY-MM-DD"
              style="width: 100%"
            />
          </div>
          <a-form-item label="选择店铺">
            <a-select
              mode="multiple"
              v-model="selectedValidationShops"
              placeholder="请选择店铺"
              style="width: 100%"
              allow-clear
              :maxTagCount="3"
            >
              <a-select-option v-for="(name, idx) in shopList" :key="idx" :value="name">
                {{ name }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-modal>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import SaleOutModal from './modules/SaleOutModal'
  import SaleBackModal from './modules/SaleBackModal'
  import BillDetail from './dialog/BillDetail'
  import BillExcelIframe from '@/components/tools/BillExcelIframe'
  import UserSelectionModal from './components/UserSelectionModal'
  import ValidationDifferencesModal from './components/ValidationDifferencesModal'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import JDate from '@/components/jeecg/JDate'
  import Vue from 'vue'
  import { postAction, getAction } from '@/api/manage'
  import moment from 'moment'
  export default {
    name: "SaleOutList",
    mixins:[JeecgListMixin,BillListMixin],
    components: {
      SaleOutModal,
      SaleBackModal,
      BillDetail,
      BillExcelIframe,
      UserSelectionModal,
      ValidationDifferencesModal,
      JEllipsis,
      JDate
    },
    data () {
      return {
        // 查询条件
        queryParam: {
          number: "",
          materialParam: "",
          type: "出库",
          subType: "销售",
          organId: undefined,
          depotId: undefined,
          creator: undefined,
          linkNumber: "",
          accountId: undefined,
          hasDebt: undefined,
          status: undefined,
          remark: "",
          shopName: undefined
        },
        shopList: [],
        prefixNo: 'XSCK',
        //出入库管理开关，适合独立仓管场景
        inOutManageFlag: false,
        // 交叉验证日期选择
        validationDateVisible: false,
        selectedValidationDate: null,
        selectedValidationShops: [],
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 默认索引
        defDataIndex:['action','operTimeStr','status','shopName','userName','materialsList','materialCount'],
        // 默认列
        defColumns: [
          {
            title: '操作',
            dataIndex: 'action',
            align:"center", width: 180,
            scopedSlots: { customRender: 'action' },
          },
          { title: '销售店铺', dataIndex: 'shopName',width:120,
            customRender:function (text) {
              return text || ''
            }
          },
          { title: '客户', dataIndex: 'organName',width:120, ellipsis:true},
          { title: '单据编号', dataIndex: 'number',width:160,
            customRender:function (text,record,index) {
              text = record.linkNumber?text+"[订]":text
              text = record.hasBackFlag?text+"[退]":text
              return text
            }
          },
          { title: '关联订单', dataIndex: 'linkNumber',width:140},
          { title: '商品信息', dataIndex: 'materialsList',width:220, ellipsis:true},
          { title: '单据日期', dataIndex: 'operTimeStr',width:145},
          { title: '操作员', dataIndex: 'userName',width:80, ellipsis:true},
          { title: '数量', dataIndex: 'materialCount',width:60},
          { title: '金额合计', dataIndex: 'totalPrice',width:80},
          { title: '含税合计', dataIndex: 'totalTaxLastMoney',width:80,
            customRender:function (text,record,index) {
              return (record.discountMoney + record.discountLastMoney).toFixed(2);
            }
          },
          { title: '优惠率', dataIndex: 'discount',width:60,
            customRender:function (text,record,index) {
              return text? text + '%':''
            }
          },
          { title: '收款优惠', dataIndex: 'discountMoney',width:80},
          { title: '其它费用', dataIndex: 'otherMoney',width:80},
          { title: '待收金额', dataIndex: 'needOutMoney',width:80,
            customRender:function (text,record,index) {
              let needOutMoney = record.discountLastMoney + record.otherMoney - record.deposit
              return needOutMoney? needOutMoney.toFixed(2):0
            }
          },
          { title: '结算账户', dataIndex: 'accountName',width:80},
          { title: '扣除订金', dataIndex: 'deposit',width:80},
          { title: '本次收款', dataIndex: 'changeAmount',width:80},
          { title: '本次欠款', dataIndex: 'debt',width:80,
            scopedSlots: { customRender: 'customRenderDebt' }
          },
          { title: '销售人员', dataIndex: 'salesManStr',width:120},
          { title: '备注', dataIndex: 'remark',width:200},
          { title: '状态', dataIndex: 'status', width: 80, align: "center",
            scopedSlots: { customRender: 'customRenderStatus' }
          }
        ],
        url: {
          list: "/depotHead/list",
          delete: "/depotHead/delete",
          deleteBatch: "/depotHead/deleteBatch",
          forceCloseBatch: "/depotHead/forceCloseBatch",
          batchSetStatusUrl: "/depotHead/batchSetStatus"
        }
      }
    },
    computed: {
    },
    created() {
      this.initSystemConfig()
      this.initShopList()
      this.initCustomer()
      this.getDepotData()
      this.initUser()
      this.initAccount()
      this.initQuickBtn()
      this.getDepotByCurrentUser()
    },
    methods: {
      initShopList() {
        console.log('Initializing shop list...');
        this.loading = true
        getAction('/shop/list').then(res => {
          console.log('Shop list response:', res);
          if (res && res.code === 200 && res.data && Array.isArray(res.data.rows)) {
            this.shopList = res.data.rows.map(row => row.name).filter(name => name)
            console.log('Shop list updated:', this.shopList);
          }
        }).finally(() => this.loading = false)
      },
      batchValidation() {
        console.log('===== batchValidation 被调用 =====');
        let that = this;
        this.$confirm({
          title: "交叉验证确认",
          content: "校验将会自动校验指定日期所有用户的未审核单据数据，只有在每个用户提交的销售单据统计数据一致时，会自动通过审核。是否继续？",
          onOk: function () {
            console.log('===== 用户确认交叉验证 =====');
            console.log('确认对话框 onOk 被调用');
            console.log('that 指向:', that);
            console.log('that.showDateSelector 类型:', typeof that.showDateSelector);
            that.showDateSelector();
          }
        });
      },
      handleValidation(validationDate, selectedShops) {
        // 执行校验逻辑
        this.loading = true;
        const requestData = {
          validationDate: validationDate,
          type: '出库',
          subType: '销售',
          shopNames: selectedShops && selectedShops.length ? JSON.stringify(selectedShops) : "[]"
        };
        postAction('/depotHead/checkTodayUsers', requestData).then((res) => {
          console.log('checkTodayUsers响应:', res);
          if(res.code === 200) {
            if(res.data.hasOtherUsers) {
              // 有其他用户，显示用户选择界面
              this.showUserSelectionModal(res.data, validationDate, '出库', '销售', selectedShops);
            } else {
              this.$message.error("校验失败：" + validationDate + " 没有其他用户保存销售出库单据！");
            }
          } else {
            this.$message.error(res.msg || "校验失败");
          }
        }).catch((error) => {
          console.error('checkTodayUsers请求错误:', error);
          this.$message.error("校验请求失败");
        }).finally(() => {
          this.loading = false;
        });
      },
      
      showUserSelectionModal(data, validationDate, type = '出库', subType = '销售', shopNames = []) {
        // 显示用户选择界面
        this.$refs.userSelectionModal.show(data, validationDate, type, subType, shopNames);
      },
      
      showDateSelector() {
        // 显示日期选择器
        console.log('showDateSelector 被调用');
        this.selectedValidationDate = moment().format('YYYY-MM-DD'); // 默认选择今天
        this.selectedValidationShops = [];
        this.validationDateVisible = true;
        console.log('validationDateVisible 设置为:', this.validationDateVisible);
        console.log('selectedValidationDate 设置为:', this.selectedValidationDate);
      },
      
      handleDateConfirm() {
        if (!this.selectedValidationDate) {
          this.$message.warning('请选择校验日期！');
          return;
        }
        if (!this.selectedValidationShops || this.selectedValidationShops.length === 0) {
          this.$message.warning('请选择需要校验的店铺！');
          return;
        }
        console.log('确认选择的日期:', this.selectedValidationDate);
        this.validationDateVisible = false;
        this.handleValidation(this.selectedValidationDate, this.selectedValidationShops);
      },
      
      handleDateCancel() {
        this.validationDateVisible = false;
        this.selectedValidationDate = null;
        this.selectedValidationShops = [];
      },
      
      showValidationDifferences(differences) {
        // 显示校验差异界面
        console.log('===== SaleOutList.showValidationDifferences 被调用 =====');
        console.log('传入的differences:', differences);
        console.log('differences类型:', typeof differences);
        console.log('ValidationDifferencesModal组件ref:', this.$refs.validationDifferencesModal);
        
        if (this.$refs.validationDifferencesModal) {
          console.log('调用ValidationDifferencesModal.show()');
          this.$refs.validationDifferencesModal.show(differences);
        } else {
          console.error('ValidationDifferencesModal组件引用未找到！');
        }
      },
      
      handleValidationSuccess(result) {
        // 处理校验成功
        console.log('校验成功:', result);
        this.$message.success(`校验通过！共有 ${result.totalBills} 种商品数据一致，相关单据状态已自动更新。`);
        this.loadData(); // 刷新列表
      },
      
      handleValidationFailed(differences) {
        // 处理校验失败，显示差异
        console.log('===== SaleOutList.handleValidationFailed 被调用 =====');
        console.log('校验失败 differences:', differences);
        this.showValidationDifferences(differences);
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'
</style>