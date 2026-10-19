package pl.vistula.checker.ui

import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.StudentServiceActions
import pl.vistula.checker.core.StudentServices

object StudentActionsController {
    fun documentDetails(activity: MainActivity, document: StudentServices.Document) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            id = R.id.student_document_details
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        UiHelper.add(activity, body, document.status, 16, UiHelper.INK, true)
        UiHelper.note(activity, body, document.typeName, UiHelper.MUTED)
        StudentCardsController.line(activity, body, R.string.documents_published, HistoryScreenController.formatFullDate(document.published))
        StudentCardsController.line(activity, body, R.string.documents_delivered, HistoryScreenController.formatFullDate(document.delivered))
        if (document.downloadable()) body.addView(UiHelper.button(activity, activity.getString(R.string.documents_download), {
            dialog.dismiss()
            activity.downloadStudentDocument("services/blobbox/get", "blob_id", document.blobId, document.name)
        }, true))
        if (document.actions.contains("RECEIVE_DOCUMENT")) {
            body.addView(UiHelper.button(activity, activity.getString(R.string.documents_get_code), {
                dialog.dismiss(); sendCode(activity, document)
            }, false))
            body.addView(UiHelper.button(activity, activity.getString(R.string.documents_receive), {
                dialog.dismiss(); receive(activity, document)
            }, false))
        }
        dialog = MaterialAlertDialogBuilder(activity).setTitle(document.name).setView(UiHelper.dialogScroll(activity, body))
            .setPositiveButton(R.string.btn_close, null).create()
        dialog.show()
    }

    fun sendCode(activity: MainActivity, document: StudentServices.Document) {
        MaterialAlertDialogBuilder(activity).setTitle(R.string.documents_get_code)
            .setMessage(activity.getString(R.string.documents_code_confirm, document.name))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.documents_get_code) { _, _ ->
                activity.studentAction("adm_documents", { client, state ->
                    StudentServiceActions.generateCode(client, state, document.id, Language.currentLocale(activity).language)
                }) { response ->
                    MaterialAlertDialogBuilder(activity).setTitle(R.string.documents_get_code)
                        .setMessage(activity.getString(R.string.documents_code_sent, StudentServices.text(response, "email"), StudentServices.text(response, "date")))
                        .setPositiveButton(R.string.documents_receive) { _, _ -> receive(activity, document) }
                        .setNegativeButton(R.string.btn_close, null).show()
                }
            }.show()
    }

    fun receive(activity: MainActivity, document: StudentServices.Document) {
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        UiHelper.note(activity, body, activity.getString(R.string.documents_receive_confirm, document.name), UiHelper.MUTED)
        val code = UiHelper.field(activity, body, activity.getString(R.string.documents_code_label), "", true)
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.documents_receive)
            .setView(UiHelper.dialogScroll(activity, body)).setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.documents_receive, null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = code.text.toString().trim()
                if (value.isEmpty()) {
                    code.error = activity.getString(R.string.api_param_required_hint)
                    return@setOnClickListener
                }
                code.setText("")
                dialog.dismiss()
                activity.studentAction("adm_documents", { client, state ->
                    StudentServiceActions.receive(client, state, document.id, value)
                }) { response ->
                    if (response.length() == 0) {
                        Toast.makeText(activity, R.string.documents_received, Toast.LENGTH_LONG).show()
                        activity.refreshStudentServices("documents")
                    } else {
                        val message = StudentServices.localized(response.opt("message"), Language.currentLocale(activity).language)
                        MaterialAlertDialogBuilder(activity).setTitle(R.string.documents_receive)
                            .setMessage(if (message.isBlank()) activity.getString(R.string.error_operation) else UiHelper.plain(message).take(1500)).setPositiveButton(R.string.btn_close, null).show()
                    }
                }
            }
        }
        dialog.setOnDismissListener { code.setText("") }
        dialog.show()
    }

    fun choosePlan(activity: MainActivity, payment: JSONObject, plan: JSONObject, description: String) {
        MaterialAlertDialogBuilder(activity).setTitle(R.string.payments_plan_choose)
            .setMessage(activity.getString(R.string.payments_plan_confirmation,
                StudentServices.localized(payment.opt("description"), Language.currentLocale(activity).language), description))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.payments_plan_choose) { _, _ ->
                activity.studentAction("payments", { client, state ->
                    StudentServiceActions.choosePlan(client, state, payment.getString("id"), payment.getString("type"), plan.getString("id"), plan)
                }) {
                    Toast.makeText(activity, R.string.payments_plan_chosen, Toast.LENGTH_LONG).show()
                    activity.refreshStudentServices("payments")
                }
            }.show()
    }
}
