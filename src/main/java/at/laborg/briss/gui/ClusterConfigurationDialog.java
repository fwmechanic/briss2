package at.laborg.briss.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;

class ClusterConfigurationDialog extends JDialog {

	private static final long serialVersionUID = -918825385363863390L;
	private static final String EVEN_ODD_QUESTION = "Cluster even and odd pages differently";
	private static final String OK_STRING = "Crop it!";

	public ClusterConfigurationDialog() {
		initUI();
	}

	private void initUI() {
		JCheckBox evenAndOddChecker = new JCheckBox(EVEN_ODD_QUESTION, true);
		JButton okButton = new JButton(OK_STRING);
		this.add(evenAndOddChecker);
		this.add(okButton);
		okButton.addActionListener(e -> {} );
	}
}
