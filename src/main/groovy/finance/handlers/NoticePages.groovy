package finance.handlers

import finance.services.UserService
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import groovy.xml.XmlUtil
import ratpack.core.handling.Context
import ratpack.core.http.Response

/**
 * Small HTML notice pages (receipt confirmation, welcome banner, status notice, invoice header)
 * that browsers and e-mail clients open directly from the links the API hands out.
 */
@CompileStatic
class NoticePages {

    interface NoticeWriter {
        void write(Response response, String message)
    }

    static class HtmlNoticeWriter implements NoticeWriter {
        void write(Response response, String message) {
            String page = "<html><body><p class=\"notice\">" + message + "</p></body></html>"
            response.contentType('text/html').send(page) // SINK: PLANTED-GR-HR-384
        }
    }

    static class EscapedNoticeWriter implements NoticeWriter {
        void write(Response response, String message) {
            String page = "<html><body><p class=\"notice\">" + XmlUtil.escapeXml(message) + "</p></body></html>"
            response.contentType('text/html').send(page) // SAFE_SINK: PLANTED-GR-HR-384-safe
        }
    }

    private static Map<String, NoticeWriter> noticeWriters() {
        Map<String, NoticeWriter> writers = new HashMap<String, NoticeWriter>()
        writers.put('html', new HtmlNoticeWriter())
        writers.put('safe', new EscapedNoticeWriter())
        return writers
    }

    private static final Map<String, NoticeWriter> NOTICE_WRITERS = noticeWriters()

    // GET /api/notice/receipt?ref=..&memo=..
    static void receipt(Context context) {
        Map<String, String> model = new LinkedHashMap<String, String>()
        model.put('ref', context.request.queryParams.get('ref') ?: 'n/a')
        model.put('memo', context.request.queryParams.get('memo') ?: '')
        String banner = receiptBanner(model)
        context.response.contentType('text/html').send(banner) // SINK: PLANTED-GR-HR-382
    }

    // GET /api/notice/receipt/summary?ref=..&memo=..
    static void receiptSummary(Context context) {
        Map<String, String> model = new LinkedHashMap<String, String>()
        model.put('ref', context.request.queryParams.get('ref') ?: 'n/a')
        model.put('memo', context.request.queryParams.get('memo') ?: '')
        String banner = receiptBannerEncoded(model)
        context.response.contentType('text/html').send(banner) // SAFE_SINK: PLANTED-GR-HR-382-safe
    }

    private static String receiptBanner(Map<String, String> model) {
        return "<div class=\"receipt\"><h3>Receipt " + model.get('ref') + "</h3><p>" + model.get('memo') + "</p></div>"
    }

    private static String receiptBannerEncoded(Map<String, String> model) {
        return "<div class=\"receipt\"><h3>Receipt " + XmlUtil.escapeXml(model.get('ref')) + "</h3><p>" +
            XmlUtil.escapeXml(model.get('memo')) + "</p></div>"
    }

    // GET /api/notice/welcome?name=..
    static void welcome(Context context, UserService userService) {
        String banner = userService.welcomeBanner(context.request.queryParams.get('name'))
        context.response.contentType('text/html').send(banner) // SINK: PLANTED-GR-HR-383
    }

    // GET /api/notice/welcome/card?name=..
    static void welcomeCard(Context context, UserService userService) {
        String banner = userService.welcomeCard(context.request.queryParams.get('name'))
        context.response.contentType('text/html').send(banner) // SAFE_SINK: PLANTED-GR-HR-383-safe
    }

    // GET /api/notice/status?fmt=html|safe&msg=..
    static void status(Context context) {
        String message = context.request.queryParams.get('msg') ?: 'All systems operational'
        NoticeWriter writer = NOTICE_WRITERS.get(context.request.queryParams.get('fmt')) ?: NOTICE_WRITERS.get('html')
        writer.write(context.response, message)
    }

    private static final String INVOICE_TEMPLATE = '<html><body><h2>Invoice for ${customer}</h2><p>Reference: ${reference}</p></body></html>'

    // GET /api/notice/invoice?customer=..&reference=..
    static void invoice(Context context) {
        Map<String, Object> binding = new HashMap<String, Object>()
        binding.put('customer', context.request.queryParams.get('customer') ?: 'unknown')
        binding.put('reference', context.request.queryParams.get('reference') ?: '-')
        String page = new SimpleTemplateEngine().createTemplate(INVOICE_TEMPLATE).make(binding).toString()
        context.response.contentType('text/html').send(page) // SINK: PLANTED-GR-HR-385
    }

    // GET /api/notice/invoice/print?customer=..&reference=..
    static void invoicePrint(Context context) {
        Map<String, Object> binding = new HashMap<String, Object>()
        binding.put('customer', XmlUtil.escapeXml(context.request.queryParams.get('customer') ?: 'unknown'))
        binding.put('reference', XmlUtil.escapeXml(context.request.queryParams.get('reference') ?: '-'))
        String page = new SimpleTemplateEngine().createTemplate(INVOICE_TEMPLATE).make(binding).toString()
        context.response.contentType('text/html').send(page) // SAFE_SINK: PLANTED-GR-HR-385-safe
    }
}
