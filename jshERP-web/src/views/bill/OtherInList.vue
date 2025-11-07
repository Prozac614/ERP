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
                  <a-form-item label="供应商" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择供应商" showSearch allow-clear optionFilterProp="children" v-model="queryParam.organId">
                      <a-select-option v-for="(item,index) in supList" :key="index" :value="item.id">
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
                  <a-form-item label="关联单据" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入关联单据" v-model="queryParam.linkNumber"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="单据状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择单据状态" allow-clear v-model="queryParam.status">
                      <a-select-option value="0">未审核</a-select-option>
                      <a-select-option value="9" v-if="!checkFlag">审核中</a-select-option>
                      <a-select-option value="1">已审核</a-select-option>
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
          <a-button v-if="inOutManageFlag && btnEnableList.indexOf(1)>-1" @click="handleWaitBill" icon="link">待入库({{waitTotal}})</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" icon="delete" @click="batchDel">删除</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(2)>-1" icon="check" @click="batchSetStatus(1)">审核</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(7)>-1" icon="stop" @click="batchSetStatus(0)">反审核</a-button>
          <a-button v-if="btnEnableList.indexOf(8)>-1 || saleBtnEnableList.indexOf(8)>-1 || hasValidationPermission" icon="eye" @click="batchValidation">校验</a-button>
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
          <a-tooltip placement="left" title="可以进行库存初始化，生产管理模块的成品入库。" slot="action">
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
              <a @click="myHandleDetail(record, '其它入库', prefixNo)">查看</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleEdit(record)">编辑</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleCopyAdd(record)">复制</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a-popconfirm v-if="btnEnableList.indexOf(1)>-1" title="确定删除吗?" @confirm="() => myHandleDelete(record)">
                <a>删除</a>
              </a-popconfirm>
            </span>
            <template slot="customRenderStatus" slot-scope="status">
              <a-tag v-if="status == '0'" color="red">未审核</a-tag>
              <a-tag v-if="status == '1'" color="green">已审核</a-tag>
              <a-tag v-if="status == '2'" color="cyan">完成入库</a-tag>
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
        <other-in-modal ref="modalForm" @ok="modalFormOk" @close="modalFormClose"></other-in-modal>
        <bill-detail ref="modalDetail" @ok="modalFormOk" @close="modalFormClose"></bill-detail>
        <bill-excel-iframe ref="billExcelIframe" @ok="modalFormOk" @close="modalFormClose"></bill-excel-iframe>
        <batch-wait-bill-list ref="batchWaitBill" @ok="modalFormOk" @close="waitModalFormClose"></batch-wait-bill-list>

        <a-modal
          title="选择校验日期"
          :visible="validationDateVisible"
          @ok="handleDateConfirm"
          @cancel="handleDateCancel"
          okText="确定"
          cancelText="取消">
          <a-form-item label="校验日期">
            <j-date
              v-model="selectedValidationDate"
              placeholder="请选择日期"
              dateFormat="YYYY-MM-DD"
              style="width: 100%"
            />
          </a-form-item>
        </a-modal>

        <user-selection-modal
          ref="userSelectionModal"
          @validation-success="handleValidationSuccess"
          @validation-failed="handleValidationFailed"
        ></user-selection-modal>

        <validation-differences-modal
          ref="validationDifferencesModal"
        ></validation-differences-modal>
      </a-card>
    </a-col>
  </a-row>
</template>
<!--power by ji shenghua-->
<script>
  import OtherInModal from './modules/OtherInModal'
  import BillDetail from './dialog/BillDetail'
  import BillExcelIframe from '@/components/tools/BillExcelIframe'
  import BatchWaitBillList from './dialog/BatchWaitBillList'
  import UserSelectionModal from './components/UserSelectionModal'
  import ValidationDifferencesModal from './components/ValidationDifferencesModal'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import JDate from '@/components/jeecg/JDate'
  import { deleteAction, postAction } from '@/api/manage'
  import Vue from 'vue'
  import moment from 'moment'
  export default {
    name: "OtherInList",
    mixins:[JeecgListMixin,BillListMixin],
    components: {
      OtherInModal,
      BillDetail,
      BillExcelIframe,
      BatchWaitBillList,
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
          type: "入库",
          subType: "其它",
          organId: undefined,
          depotId: undefined,
          creator: undefined,
          linkNumber: "",
          status: undefined,
          remark: ""
        },
        prefixNo: 'QTRK',
        //出入库管理开关，适合独立仓管场景
        inOutManageFlag: false,
        // 销售出库按钮权限串，复用校验按钮权限
        saleBtnEnableList: '',
        // 交叉验证日期选择
        validationDateVisible: false,
        selectedValidationDate: null,
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 默认索引
        defDataIndex:['action','userName','materialsList','materialCount','status'],
        // 默认列
        defColumns: [
          {
            title: '操作',
            dataIndex: 'action',
            align:"center", width: 160,
            scopedSlots: { customRender: 'action' },
          },
          { title: '供应商', dataIndex: 'organName',width:120, ellipsis:true},
          { title: '单据编号', dataIndex: 'number',width:160,
            customRender:function (text,record,index) {
              text = record.linkNumber?text+"[转]":text
              return text
            }
          },
          { title: '关联单据', dataIndex: 'linkNumber',width:140},
          { title: '商品信息', dataIndex: 'materialsList',width:220, ellipsis:true},
          { title: '单据日期', dataIndex: 'operTimeStr',width:145},
          { title: '操作员', dataIndex: 'userName',width:80, ellipsis:true},
          { title: '数量', dataIndex: 'materialCount',width:60},
          { title: '金额合计', dataIndex: 'totalPrice',width:80},
          { title: '备注', dataIndex: 'remark',width:200},
          { title: '状态', dataIndex: 'status', width: 80, align: "center",
            scopedSlots: { customRender: 'customRenderStatus' }
          }
        ],
        url: {
          list: "/depotHead/list",
          delete: "/depotHead/delete",
          deleteBatch: "/depotHead/deleteBatch",
          batchSetStatusUrl: "/depotHead/batchSetStatus"
        }
      }
    },
    computed: {
      hasValidationPermission() {
        const btnStrList = Vue.ls.get('winBtnStrList')
        if (btnStrList) {
          for (let i = 0; i < btnStrList.length; i++) {
            const item = btnStrList[i]
            if (item.url && (
              item.url.includes('sale_out') ||
              item.url.includes('sale-out') ||
              item.url.includes('saleOut') ||
              item.url.includes('销售出库')
            )) {
              if (item.btnStr && item.btnStr.length > 8) {
                return item.btnStr.charAt(8) === '1'
              }
            }
          }
        }
        return false
      }
    },
    created() {
      this.initSystemConfig()
      this.initSupplier()
      this.getDepotData()
      this.initUser()
      this.initWaitBillCount('入库', '采购,销售退货', '1,3')
      this.initSaleBtnStr()
    },
    methods: {
      searchQuery() {
        this.loadData(1)
        if(this.inOutManageFlag) {
          this.initWaitBillCount('入库', '采购,销售退货', '1,3')
        }
      },
      searchReset() {
        this.queryParam = {
          type: this.queryParam.type,
          subType: this.queryParam.subType
        }
        this.loadData(1)
        if(this.inOutManageFlag) {
          this.initWaitBillCount('入库', '采购,销售退货', '1,3')
        }
      },
      myHandleDelete(record) {
        let that = this
        if(record.status === '0') {
          deleteAction(that.url.delete, {id: record.id}).then((res) => {
            if(res.code === 200){
              that.loadData(1)
              if(that.inOutManageFlag) {
                that.initWaitBillCount('入库', '采购,销售退货', '1,3')
              }
            } else {
              that.$message.warning(res.data.message);
            }
          })
        } else {
          this.$message.warning("抱歉，只有未审核的单据才能删除，请先进行反审核！")
        }
      },
      batchDel: function () {
        if (this.selectedRowKeys.length <= 0) {
          this.$message.warning('请选择一条记录！')
        } else {
          let ids = ""
          for (let i = 0; i < this.selectedRowKeys.length; i++) {
            ids += this.selectedRowKeys[i] + ","
          }
          let that = this
          this.$confirm({
            title: "确认删除",
            content: "是否删除选中数据?",
            onOk: function () {
              that.loading = true
              deleteAction(that.url.deleteBatch, {ids: ids}).then((res) => {
                if(res.code === 200){
                  that.loadData()
                  that.onClearSelected()
                  if(that.inOutManageFlag) {
                    that.initWaitBillCount('入库', '采购,销售退货', '1,3')
                  }
                } else {
                  that.$message.warning(res.data.message)
                }
              }).finally(() => {
                that.loading = false
              })
            }
          })
        }
      },
      //待入库
      handleWaitBill() {
        this.$refs.batchWaitBill.show('入库', '采购,销售退货', "1,3")
        this.$refs.batchWaitBill.title = "批量选择采购入库或销售退货"
      },
      waitModalFormClose() {
        this.loadData()
        this.initWaitBillCount('入库', '采购,销售退货', '1,3')
      },
      initSaleBtnStr() {
        const btnStrList = Vue.ls.get('winBtnStrList')
        this.saleBtnEnableList = ''
        if (btnStrList) {
          for (let i = 0; i < btnStrList.length; i++) {
            const item = btnStrList[i]
            if (item.url === '/bill/sale_out' ||
              item.url === '/sale_out' ||
              (item.url && item.url.includes('sale_out')) ||
              (item.url && item.url.includes('销售出库'))) {
              if (item.btnStr) {
                this.saleBtnEnableList = item.btnStr
              }
              break
            }
          }
        }
      },
      batchValidation() {
        const that = this
        this.$confirm({
          title: "交叉验证确认",
          content: "校验将会自动校验指定日期所有用户的未审核单据数据，只有在每个用户提交的其它入库单据统计数据一致时，会自动通过审核。是否继续？",
          onOk() {
            that.showDateSelector()
          }
        })
      },
      showDateSelector() {
        this.selectedValidationDate = moment().format('YYYY-MM-DD')
        this.validationDateVisible = true
      },
      handleDateConfirm() {
        if (!this.selectedValidationDate) {
          this.$message.warning('请选择校验日期！')
          return
        }
        let formattedDate
        if (moment.isMoment(this.selectedValidationDate)) {
          formattedDate = this.selectedValidationDate.format('YYYY-MM-DD')
        } else if (this.selectedValidationDate instanceof Date) {
          formattedDate = moment(this.selectedValidationDate).format('YYYY-MM-DD')
        } else if (typeof this.selectedValidationDate === 'string') {
          formattedDate = moment(this.selectedValidationDate).format('YYYY-MM-DD')
        } else {
          this.$message.error('日期格式无效')
          return
        }
        this.validationDateVisible = false
        this.handleValidation(formattedDate)
      },
      handleDateCancel() {
        this.validationDateVisible = false
        this.selectedValidationDate = null
      },
      handleValidation(validationDate) {
        this.loading = true
        const requestData = {
          validationDate,
          type: '入库',
          subType: '其它'
        }
        postAction('/depotHead/checkTodayUsers', requestData).then((res) => {
          if (res.code === 200) {
            if (res.data.hasOtherUsers) {
              this.showUserSelectionModal(res.data, validationDate)
            } else {
              this.$message.error(`校验失败：${validationDate} 没有其他用户保存其它入库单据！`)
            }
          } else {
            this.$message.error(res.msg || '校验失败')
          }
        }).catch((error) => {
          console.error('checkTodayUsers请求错误:', error)
          this.$message.error('校验请求失败')
        }).finally(() => {
          this.loading = false
        })
      },
      showUserSelectionModal(data, validationDate) {
        if (this.$refs.userSelectionModal) {
          this.$refs.userSelectionModal.show(data, validationDate, '入库', '其它')
        }
      },
      handleValidationSuccess(result) {
        this.$message.success(`校验通过！共有 ${result.totalBills} 种商品数据一致，相关单据状态已自动更新。`)
        this.loadData()
      },
      handleValidationFailed(differences) {
        this.showValidationDifferences(differences)
      },
      showValidationDifferences(differences) {
        if (this.$refs.validationDifferencesModal) {
          this.$refs.validationDifferencesModal.show(differences)
        } else {
          console.error('ValidationDifferencesModal组件引用未找到！')
        }
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'
</style>