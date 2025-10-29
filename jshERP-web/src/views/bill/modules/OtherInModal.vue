<template>
  <j-modal
    :title="title"
    :width="width"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :keyboard="false"
    :forceRender="true"
    v-bind:prefixNo="prefixNo"
    fullscreen
    switchFullscreen
    @cancel="handleCancel"
    :id="prefixNo"
    style="top:20px;height: 95%;">
    <template slot="footer">
      <a-button @click="handleCancel">取消</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrintPro('其它入库')">三联打印新版预览</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrint('其它入库')">三联打印预览</a-button>
      <a-button v-if="checkFlag && isCanCheck" :loading="confirmLoading" @click="handleOkAndCheck">保存并审核</a-button>
      <a-button type="primary" :loading="confirmLoading" @click="handleOk">保存（Ctrl+S）</a-button>
      <!--发起多级审核-->
      <a-button v-if="!checkFlag" @click="handleWorkflow()" type="primary">提交流程</a-button>
    </template>
    <a-spin :spinning="confirmLoading">
      <a-form :form="form">
        <a-row class="form-row" :gutter="24">
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="供应商">
              <a-select placeholder="请选择供应商" v-decorator="[ 'organId' ]" :disabled="!rowCanEdit"
                :dropdownMatchSelectWidth="false" showSearch optionFilterProp="children">
                <div slot="dropdownRender" slot-scope="menu">
                  <v-nodes :vnodes="menu" />
                  <a-divider style="margin: 4px 0;" />
                  <div v-if="quickBtn.vendor" class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="addSupplier"><a-icon type="plus" /> 新增供应商</div>
                  <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initSupplier(0)"><a-icon type="reload" /> 刷新列表</div>
                </div>
                <a-select-option v-for="(item,index) in supList" :key="index" :value="item.id">
                  {{ item.supplier }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据日期">
              <j-date v-decorator="['operTime', validatorRules.operTime]" :show-time="true"/>
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据编号">
              <a-input placeholder="请输入单据编号" v-decorator.trim="[ 'number' ]" />
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item v-if="inOutManageFlag && !model.billType" :labelCol="labelCol" :wrapperCol="wrapperCol" label="关联单据">
              <a-input-search placeholder="请选择待入库单据" v-decorator="[ 'linkNumber' ]" @search="onSearchLinkNumber" :readOnly="true"/>
            </a-form-item>
          </a-col>
        </a-row>
        <j-editable-table id="billModal"
          :ref="refKeys[0]"
          :loading="materialTable.loading"
          :columns="materialTable.columns"
          :dataSource="materialTable.dataSource"
          :minWidth="minWidth"
          :maxHeight="300"
          :rowNumber="false"
          :rowSelection="rowCanEdit"
          :actionButton="rowCanEdit"
          :dragSortAndNumber="rowCanEdit"
          @valueChange="onValueChange"
          @added="onAdded"
          @deleted="onDeleted">
          <template #buttonAfter>
            <a-row v-if="rowCanEdit" :gutter="24" style="float:left;" data-step="4" data-title="扫码录入" data-intro="此功能支持扫码枪扫描商品唛头进行录入">
              <a-col v-if="scanStatus" :md="6" :sm="24">
                <a-button @click="scanEnter">扫码录入</a-button>
              </a-col>
              <a-col v-if="!scanStatus" :md="16" :sm="24" style="padding: 0 8px 0 12px">
                <a-input placeholder="请扫描商品唛头并回车" v-model="scanBarCode" @pressEnter="scanPressEnter" ref="scanBarCode"/>
              </a-col>
              <a-col v-if="!scanStatus" :md="6" :sm="24" style="padding: 0px 12px 0 0">
                <a-button @click="stopScan">收起扫码</a-button>
              </a-col>
            </a-row>
            <a-row v-if="rowCanEdit" :gutter="24" style="float:left;padding-bottom: 5px;padding-left:20px;">
              <a-button icon="import" @click="onImport(prefixNo)">导入明细</a-button>
            </a-row>
          </template>
          <template #depotBatchSet>
            <a-icon type="down" @click="handleBatchSetDepot" />
          </template>
          <template #depotAdd>
            <a-divider v-if="quickBtn.depot" style="margin: 4px 0;" />
            <div v-if="quickBtn.depot" class="dropdown-btn" @click="addDepot"><a-icon type="plus" /> 新增</div>
            <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initDepot"><a-icon type="reload" /> 刷新</div>
          </template>
        </j-editable-table>
        <a-row class="form-row" :gutter="24">
          <a-col :lg="24" :md="24" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="{xs: { span: 24 },sm: { span: 24 }}" label="">
              <a-textarea :rows="1" placeholder="请输入备注" v-decorator="[ 'remark' ]" style="margin-top:8px;"/>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row class="form-row" :gutter="24">
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="附件">
              <j-upload v-model="fileList" bizPath="bill"></j-upload>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-spin>
    <vendor-modal ref="vendorModalForm" @ok="vendorModalFormOk"></vendor-modal>
    <depot-modal ref="depotModalForm" @ok="depotModalFormOk"></depot-modal>
    <batch-set-depot ref="batchSetDepotModalForm" @ok="batchSetDepotModalFormOk"></batch-set-depot>
    <import-item-modal ref="importItemModalForm" @ok="importItemModalFormOk"></import-item-modal>
    <wait-bill-list ref="waitBillList" @ok="waitBillListOk"></wait-bill-list>
    <workflow-iframe ref="modalWorkflow" @ok="workflowModalFormOk"></workflow-iframe>
    <bill-print-iframe ref="modalPrint"></bill-print-iframe>
    <bill-print-pro-iframe ref="modalPrintPro"></bill-print-pro-iframe>
  </j-modal>
</template>
<script>
  import pick from 'lodash.pick'
  import VendorModal from '../../system/modules/VendorModal'
  import DepotModal from '../../system/modules/DepotModal'
  import BatchSetDepot from '../dialog/BatchSetDepot'
  import ImportItemModal from '../dialog/ImportItemModal'
  import WorkflowIframe from '@/components/tools/WorkflowIframe'
  import BillPrintIframe from '../dialog/BillPrintIframe'
  import BillPrintProIframe from '../dialog/BillPrintProIframe'
  import { FormTypes } from '@/utils/JEditableTableUtil'
  import { JEditableTableMixin } from '@/mixins/JEditableTableMixin'
  import { BillModalMixin } from '../mixins/BillModalMixin'
  import { getMpListShort } from "@/utils/util"
  import JUpload from '@/components/jeecg/JUpload'
  import JDate from '@/components/jeecg/JDate'
  import Vue from 'vue'
  import WaitBillList from '../dialog/WaitBillList'
  import { getAction } from '@/api/manage'
  export default {
    name: "OtherInModal",
    mixins: [JEditableTableMixin, BillModalMixin],
    components: {
      VendorModal,
      DepotModal,
      BatchSetDepot,
      ImportItemModal,
      WaitBillList,
      WorkflowIframe,
      BillPrintIframe,
      BillPrintProIframe,
      JUpload,
      JDate,
      VNodes: {
        functional: true,
        render: (h, ctx) => ctx.props.vnodes,
      }
    },
    data () {
      return {
        title:"操作",
        width: '1600px',
        moreStatus: false,
        // 新增时子表默认添加几行空数据
        addDefaultRowNum: 20,
        visible: false,
        operTimeStr: '',
        prefixNo: 'QTRK',
        fileList:[],
        rowCanEdit: true,
        //出入库管理开关，适合独立仓管场景
        inOutManageFlag: false,
        model: {},
        // 滚动加载相关
        scrollLoadThreshold: 20,  // 距离底部20px时加载
        scrollLoadRowCount: 1,    // 每次加载1行
        isScrollLoading: false,   // 防止重复加载
        labelCol: {
          xs: { span: 24 },
          sm: { span: 8 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        refKeys: ['materialDataTable', ],
        activeKey: 'materialDataTable',
        materialTable: {
          loading: false,
          dataSource: [],
          columns: [
            { title: '仓库名称', key: 'depotId', width: '8%', type: FormTypes.select, placeholder: '请选择${title}', options: [],
              allowSearch:true, validateRules: [{ required: true, message: '${title}不能为空' }]
            },
            { title: '唛头', key: 'barCode', width: '12%', type: FormTypes.popupJsh, kind: 'material', multi: true
            },
            { title: '名称', key: 'name', width: '10%', type: FormTypes.normal },
            { title: '规格', key: 'standard', width: '9%', type: FormTypes.normal },
            { title: '型号', key: 'model', width: '9%', type: FormTypes.normal },
            { title: '颜色', key: 'color', width: '5%', type: FormTypes.normal },
            { title: '品牌', key: 'brand', width: '6%', type: FormTypes.normal },
            { title: '制造商', key: 'mfrs', width: '6%', type: FormTypes.normal },
            { title: '扩展1', key: 'otherField1', width: '4%', type: FormTypes.normal },
            { title: '扩展2', key: 'otherField2', width: '4%', type: FormTypes.normal },
            { title: '扩展3', key: 'otherField3', width: '4%', type: FormTypes.normal },
            { title: '库存', key: 'stock', width: '5%', type: FormTypes.normal },
            { title: '单位', key: 'unit', width: '4%', type: FormTypes.normal },
            { title: '序列号', key: 'snList', width: '12%', type: FormTypes.popupJsh, kind: 'snAdd', multi: true },
            { title: '批号', key: 'batchNumber', width: '7%', type: FormTypes.input },
            { title: '有效期', key: 'expirationDate',width: '7%', type: FormTypes.date },
            { title: '多属性', key: 'sku', width: '9%', type: FormTypes.normal },
            { title: '原数量', key: 'preNumber', width: '4%', type: FormTypes.normal },
            { title: '已入库', key: 'finishNumber', width: '4%', type: FormTypes.normal },
            { title: '数量', key: 'operNumber', width: '5%', type: FormTypes.inputNumber, statistics: true,
              validateRules: [{ required: true, message: '${title}不能为空' }]
            },
            { title: '单价', key: 'unitPrice', width: '5%', type: FormTypes.normal},
            { title: '金额', key: 'allPrice', width: '5%', type: FormTypes.normal, statistics: true },
            { title: '备注', key: 'remark', width: '5%', type: FormTypes.input },
            { title: '关联id', key: 'linkId', width: '5%', type: FormTypes.hidden },
          ]
        },
        confirmLoading: false,
        validatorRules:{
          operTime:{
            rules: [
              { required: true, message: '请输入单据日期!' }
            ]
          },
          type:{
            rules: [
              { required: true, message: '请选择类型!' }
            ]
          }
        },
        url: {
          add: '/depotHead/addDepotHeadAndDetail',
          edit: '/depotHead/updateDepotHeadAndDetail',
          detailList: '/depotItem/getDetailList'
        }
      }
    },
    created () {
    },
    beforeDestroy() {
      // 清理滚动事件监听器
      const tableRef = this.$refs[this.refKeys[0]];
      if (tableRef && tableRef.$refs.scrollView) {
        tableRef.$refs.scrollView.removeEventListener('scroll', this.handleTableScroll);
      }
    },
    methods: {
      //调用完edit()方法之后会自动调用此方法
      editAfter() {
        this.billStatus = '0'
        this.currentSelectDepotId = ''
        this.rowCanEdit = true
        this.materialTable.columns[1].type = FormTypes.popupJsh
        this.changeColumnHide()
        this.changeFormTypes(this.materialTable.columns, 'snList', 0)
        this.changeFormTypes(this.materialTable.columns, 'batchNumber', 0)
        this.changeFormTypes(this.materialTable.columns, 'expirationDate', 0)
        this.changeFormTypes(this.materialTable.columns, 'preNumber', 0)
        this.changeFormTypes(this.materialTable.columns, 'finishNumber', 0)
        if (this.action === 'add') {
          this.addInit(this.prefixNo)
          this.fileList = []
          
          this.$nextTick(() => {
            // 初始化后滚动到顶部
            this.scrollToTop();
            // 模拟为每一行触发onAdded事件
            this.triggerOnAddedForAllRows();
          })
        } else {
          if(this.model.linkNumber) {
            this.rowCanEdit = false
            this.materialTable.columns[1].type = FormTypes.normal
          }
          this.model.operTime = this.model.operTimeStr
          this.fileList = this.model.fileName
          this.$nextTick(() => {
            this.form.setFieldsValue(pick(this.model,'organId', 'operTime', 'number', 'linkNumber', 'remark',
              'discount','discountMoney','discountLastMoney','otherMoney','accountId','changeAmount'))
          });
          // 加载子表数据
          let params = {
            headerId: this.model.id,
            mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
            linkType: 'basic'
          }
          let url = this.readOnly ? this.url.detailList : this.url.detailList;
          this.requestSubTableData(url, params, this.materialTable);
        }
        //复制新增单据-初始化单号和日期
        if(this.action === 'copyAdd') {
          this.model.id = ''
          this.model.tenantId = ''
          this.copyAddInit(this.prefixNo)
        }
        this.initSystemConfig()
        this.initSupplier(0)
        this.initDepot()
        this.initPlatform()
        this.initQuickBtn()
        this.handleChangeOtherField()
        
        // 新增时初始化操作
        if (this.action === 'add') {
          this.$nextTick(() => {
            // 初始化后滚动到顶部
            this.scrollToTop();
            // 为初始20行设置默认仓库值
            this.setInitialDepotForRows();
          });
        }
        
        // 绑定滚动事件监听器
        this.$nextTick(() => {
          const tableRef = this.$refs[this.refKeys[0]];
          if (tableRef && tableRef.$refs.scrollView) {
            tableRef.$refs.scrollView.addEventListener('scroll', this.handleTableScroll);
          }
        });
      },
      // 处理表格滚动事件
      handleTableScroll() {
        if (this.isScrollLoading) return;
        
        const tableRef = this.$refs[this.refKeys[0]];
        if (!tableRef) return;
        
        const scrollView = tableRef.$refs.scrollView;
        if (!scrollView) return;
        
        const { scrollTop, scrollHeight, clientHeight } = scrollView;
        const distanceToBottom = scrollHeight - scrollTop - clientHeight;
        
        // 当距离底部小于阈值时触发加载
        if (distanceToBottom <= this.scrollLoadThreshold) {
          this.addMoreRows();
        }
      },

      // 添加更多行
      addMoreRows() {
        this.isScrollLoading = true;
        const tableRef = this.$refs[this.refKeys[0]];
        if (tableRef && tableRef.add) {
          tableRef.add(this.scrollLoadRowCount);
        }
        // 使用setTimeout防止过于频繁的触发
        setTimeout(() => {
          this.isScrollLoading = false;
        }, 200);
      },
      
      // 为初始行设置默认仓库（修复版本）
      setInitialDepotForRows() {
        this.$nextTick(() => {
          const tableRef = this.$refs[this.refKeys[0]];
          if (!tableRef || !tableRef.rows || tableRef.rows.length === 0) {
            // 如果表格还没初始化，等待一下再试
            setTimeout(() => this.setInitialDepotForRows(), 500);
            return;
          }
          
          if (this.currentSelectDepotId) {
            // 如果已经有选中的仓库，直接为所有行设置
            const values = tableRef.rows.map(row => ({
              rowKey: row.id,
              values: { depotId: this.currentSelectDepotId }
            }));
            tableRef.setValues(values);
          } else {
            // 只调用一次API，然后为所有行设置
            getAction('/depot/findDepotByCurrentUser').then((res) => {
              if (res.code === 200) {
                let arr = res.data;
                let defaultDepotId = '';
                
                if (arr.length === 1) {
                  defaultDepotId = arr[0].id + '';
                } else {
                  for (let i = 0; i < arr.length; i++) {
                    if (arr[i].isDefault) {
                      defaultDepotId = arr[i].id + '';
                      break;
                    }
                  }
                }
                
                if (defaultDepotId) {
                  // 为所有行一次性设置默认仓库
                  const values = tableRef.rows.map(row => ({
                    rowKey: row.id,
                    values: { depotId: defaultDepotId }
                  }));
                  tableRef.setValues(values);
                }
              }
            });
          }
        });
      },
      
      // 滚动到顶部
      scrollToTop() {
        this.$nextTick(() => {
          const tableRef = this.$refs[this.refKeys[0]];
          if (tableRef && tableRef.$refs.scrollView) {
            tableRef.$refs.scrollView.scrollTop = 0;
          }
        });
      },
      

      

      //提交单据时整理成formData
      classifyIntoFormData(allValues) {
        let totalPrice = 0
        let billMain = Object.assign(this.model, allValues.formValue)
        let detailArr = allValues.tablesValue[0].values
        
        // 过滤掉唄头为空的行（数量现在是必填的）
        detailArr = detailArr.filter(item => {
          const hasBarCode = item.barCode && item.barCode.trim() !== '';
          return hasBarCode;
        });
        billMain.type = '入库'
        billMain.subType = '其它'
        for(let item of detailArr){
          totalPrice += item.allPrice-0
        }
        billMain.totalPrice = 0-totalPrice
        if(this.fileList && this.fileList.length > 0) {
          billMain.fileName = this.fileList
        } else {
          billMain.fileName = ''
        }
        if(this.model.id){
          billMain.id = this.model.id
        }
        billMain.status = this.billStatus
        return {
          info: JSON.stringify(billMain),
          rows: JSON.stringify(detailArr),
        }
      },
      onSearchLinkNumber() {
        this.$refs.waitBillList.show('入库', '采购,销售退货', "1,3")
        this.$refs.waitBillList.title = "请选择采购入库或销售退货"
      },
      waitBillListOk(selectBillDetailRows, linkNumber, remark) {
        this.rowCanEdit = false
        this.materialTable.columns[1].type = FormTypes.normal
        this.changeFormTypes(this.materialTable.columns, 'preNumber', 1)
        this.changeFormTypes(this.materialTable.columns, 'finishNumber', 1)
        if(selectBillDetailRows && selectBillDetailRows.length>0) {
          let listEx = []
          for(let j=0; j<selectBillDetailRows.length; j++) {
            let info = selectBillDetailRows[j];
            if(info.finishNumber>0) {
              info.operNumber = info.preNumber - info.finishNumber
              // 计算金额与税额
              let taxRate = info.taxRate-0
              info.allPrice = (info.operNumber * info.unitPrice).toFixed(2)-0
              info.taxMoney = (info.allPrice*taxRate/100).toFixed(2)-0
              info.taxLastMoney = (info.allPrice + info.taxMoney).toFixed(2)-0
            }
            info.linkId = info.id
            listEx.push(info)
            this.changeColumnShow(info)
          }
          this.materialTable.dataSource = listEx
          this.$nextTick(() => {
            this.form.setFieldsValue({
              'linkNumber': linkNumber,
              'remark': remark
            })
          })
        }
      },
      
      // 重写onAdded方法，防止自动滚动但保留仓库设置逻辑
      onAdded(event) {
        console.log('重写的onAdded被调用，行数:', event.target.rows.length);
        const { row, target } = event
        target.setValues([{ rowKey: row.id, values: { operNumber: 0 } }])
        
        // 保留原来的仓库设置逻辑
        if (this.currentSelectDepotId) {
          target.setValues([{ rowKey: row.id, values: { depotId: this.currentSelectDepotId } }])
        } else {
          getAction('/depot/findDepotByCurrentUser').then((res) => {
            if (res.code === 200) {
              let arr = res.data
              if (arr.length === 1) {
                target.setValues([{ rowKey: row.id, values: { depotId: arr[0].id + '' } }])
              } else {
                for (let i = 0; i < arr.length; i++) {
                  if (arr[i].isDefault) {
                    target.setValues([{ rowKey: row.id, values: { depotId: arr[i].id + '' } }])
                    break
                  }
                }
              }
            }
          })
        }
      },
      
      // 为所有初始行触发onAdded逻辑
      triggerOnAddedForAllRows() {
        setTimeout(() => {
          const tableRef = this.$refs[this.refKeys[0]];
          if (!tableRef || !tableRef.rows || tableRef.rows.length === 0) {
            setTimeout(() => this.triggerOnAddedForAllRows(), 500);
            return;
          }
          
          tableRef.rows.forEach(row => {
            const mockEvent = {
              row: row,
              target: tableRef
            };
            this.onAdded(mockEvent);
          });
        }, 1000);
      },
      
      // 滚动到顶部
      scrollToTop() {
        const attemptScroll = () => {
          const tableRef = this.$refs[this.refKeys[0]];
          if (tableRef && tableRef.$refs.scrollView) {
            tableRef.$refs.scrollView.scrollTop = 0;
            setTimeout(() => {
              if (tableRef.$refs.scrollView.scrollTop > 0) {
                attemptScroll();
              }
            }, 100);
          }
        };
        
        this.$nextTick(() => {
          attemptScroll();
          setTimeout(attemptScroll, 500);
          setTimeout(attemptScroll, 1000);
        });
      }
    }
  }
</script>
<style scoped>

</style>